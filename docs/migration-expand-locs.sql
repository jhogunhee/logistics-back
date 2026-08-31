    -- =====================================================================
    -- 로케이션 확장 — 개발용 최소 구성(보관 14자리)을 실제 센터 규모(보관 96 + 피킹 14)로 늘린다.
    --   전제: docs/migration-add-mnu.sql 까지 적용된 DB.
    --
    --   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
    --     ON CONFLICT DO NOTHING과 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
    --
    --   왜 늘리나 — 적치 도면(랙×층 격자)·추천 순위·점유 맵이 전부 「자리가 여럿일 때」를 전제로
    --   만든 화면인데, 상온 보관이 6자리뿐이라 그 판단이 보이지 않았다. 존·상품·재고는 그대로 두고
    --   자리만 늘린다(빈 랙으로 들어가므로 기존 재고·지시·할당에 영향이 없다).
    --
    --   구성
    --     DRY  통로 A~D × 랙 01~05 × 1~3층 = 60자리
    --     CHL  통로 A~B × 랙 01~04 × 1~3층 = 24자리
    --     FRZ  통로 A~B × 랙 01~03 × 1~2층 = 12자리
    --     PIK-DRY 6 · PIK-CHL 4 · PIK-FRZ 4 (피킹 페이스는 랙 하나에 층만 여럿)
    --
    --   적재 상한은 층으로 가른다 — 1층 1200 / 2층 1000 / 3층 800. 아래가 무거운 것을 놓는
    --   자리라 크고, 위로 갈수록 작다. 피킹존은 페이스라 200으로 작게 둔다(기존 시드와 같다).
    --
    --   적치 우선순위(ptawy_prty)는 통로 → 랙 → 층 순으로 다시 매긴다. 도면의 추천 순위가 이 값을
    --   그대로 쓰므로, 새 자리만 뒤에 붙이면 「A통로 1번 랙」이 아니라 옛 6자리가 늘 1~3순위가 된다.
    --
    --   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
    -- =====================================================================

    DO $loc$
    DECLARE
        v_zon_id    BIGINT;
        v_aisle     TEXT;
        v_bay       INT;
        v_level     INT;
        v_cd        TEXT;
        v_max       BIGINT;
        v_prty      INT;
        v_added     INT := 0;
        r           RECORD;
    BEGIN
        -- 1. 보관존 — 통로 × 랙 × 층 -------------------------------------
        FOR r IN
            SELECT * FROM (VALUES
                ('DRY'::TEXT, ARRAY['A','B','C','D']::TEXT[], 5, 3),
                ('CHL', ARRAY['A','B'],                     4, 3),
                ('FRZ', ARRAY['A','B'],                     3, 2)
            ) AS t(zon_cd, aisles, bays, levels)
        LOOP
            SELECT zon_id INTO v_zon_id FROM zon WHERE zon_cd = r.zon_cd;
            IF v_zon_id IS NULL THEN
                RAISE NOTICE '% 존이 없다 — 건너뜀', r.zon_cd;
                CONTINUE;
            END IF;

            FOREACH v_aisle IN ARRAY r.aisles LOOP
                FOR v_bay IN 1..r.bays LOOP
                    FOR v_level IN 1..r.levels LOOP
                        v_cd := format('%s-%s-%s-%s', r.zon_cd, v_aisle,
                                       lpad(v_bay::TEXT, 2, '0'), lpad(v_level::TEXT, 2, '0'));
                        v_max := CASE v_level WHEN 1 THEN 1200 WHEN 2 THEN 1000 ELSE 800 END;
                        -- 통로(A=1..) → 랙 → 층. 세 자리씩 띄워 나중에 사이에 끼울 여지를 남긴다
                        v_prty := (ascii(v_aisle) - ascii('A') + 1) * 100 + v_bay * 10 + v_level;

                        INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
                        VALUES (v_cd, v_zon_id, r.zon_cd, 'STORAGE', v_prty, v_prty, v_max)
                        ON CONFLICT (loc_cd) DO NOTHING;

                        IF FOUND THEN
                            v_added := v_added + 1;
                        END IF;
                        -- 이미 있던 자리는 우선순위만 새 체계로 맞춘다 — 옛 6자리가 늘 앞순위로 남지 않게
                        UPDATE loc SET pikng_prty = v_prty, ptawy_prty = v_prty
                         WHERE loc_cd = v_cd AND (pikng_prty <> v_prty OR ptawy_prty <> v_prty);
                    END LOOP;
                END LOOP;
            END LOOP;
        END LOOP;
        RAISE NOTICE '보관 로케이션 % 자리 추가', v_added;

        -- 2. 피킹존 — 랙 하나에 층만 여럿 --------------------------------
        --    pikng_prty 0: 보관(1~)보다 앞이라 FEFO 동순위에서 먼저 할당된다 (기존 시드 규칙)
        --    ptawy_prty 900+: 적치 동선의 후순위 — 피킹 페이스는 보충으로 채우는 자리다
        v_added := 0;
        FOR r IN
            SELECT * FROM (VALUES
                ('PIK-DRY'::TEXT, 'DRY'::TEXT, 6),
                ('PIK-CHL', 'CHL', 4),
                ('PIK-FRZ', 'FRZ', 4)
            ) AS t(zon_cd, tmp_zon, levels)
        LOOP
            SELECT zon_id INTO v_zon_id FROM zon WHERE zon_cd = r.zon_cd;
            CONTINUE WHEN v_zon_id IS NULL;

            FOR v_level IN 1..r.levels LOOP
                v_cd := format('%s-01-%s', r.zon_cd, lpad(v_level::TEXT, 2, '0'));
                INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
                VALUES (v_cd, v_zon_id, r.tmp_zon, 'STORAGE', 0, 900 + v_level, 200)
                ON CONFLICT (loc_cd) DO NOTHING;
                IF FOUND THEN
                    v_added := v_added + 1;
                END IF;
                -- 이미 있던 피킹 자리도 새 체계로 맞춘다 — 옛 값(9)이 보관존(111~)보다 작아
                -- 적치 추천 1순위가 피킹존이 되어 버린다. 피킹 페이스는 보충이 채우는 자리이지
                -- 입고 물량을 처음 내려놓는 자리가 아니다
                UPDATE loc SET ptawy_prty = 900 + v_level
                 WHERE loc_cd = v_cd AND ptawy_prty <> 900 + v_level;
            END LOOP;
        END LOOP;
        RAISE NOTICE '피킹 로케이션 % 자리 추가', v_added;

        RAISE NOTICE '로케이션 확장 완료 — 전체 % 자리', (SELECT COUNT(*) FROM loc);
    END
    $loc$;

    -- =====================================================================
    -- 적용 후 확인
    --   1) 존별 자리 수
    --      SELECT z.zon_cd, COUNT(*) FROM loc l JOIN zon z ON z.zon_id = l.zon_id
    --       GROUP BY z.zon_cd ORDER BY z.zon_cd;
    --      -- DRY 60 · CHL 24 · FRZ 12 · PIK-DRY 6 · PIK-CHL 4 · PIK-FRZ 4
    --      --   + RTN-* 각 1 · RCV-STAGE 1 · SHIP-STAGE 1 = 총 114
    --   2) 적치 우선순위가 통로 → 랙 → 층 순인가
    --      SELECT loc_cd, ptawy_prty FROM loc WHERE loc_cd LIKE 'DRY-%' ORDER BY ptawy_prty LIMIT 10;
    --      -- DRY-A-01-01(111) → DRY-A-01-02(112) → DRY-A-01-03(113) → DRY-A-02-01(121) …
    --   3) 재고·지시는 그대로인가 (자리만 늘렸으므로 변화가 없어야 한다)
    --      SELECT COUNT(*) FROM inv;  SELECT COUNT(*) FROM putaway_task;
    -- =====================================================================
