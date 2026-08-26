-- =====================================================================
-- 개발용 시드 — 입고예정(SCHEDULED) ASN을 「검수까지」 진행시킨다.
--
--   목적 — 적치할 물량을 만든다. 적치 맵(드래그앤드롭)이나 적치지시 화면을 시험하려면
--   RCV-STAGE에 재고가 있어야 하는데, docs/seed-dev.sql은 입고예정 ASN까지만 만들고 멈춘다.
--   이 파일이 그 자리에서 이어받아 ReceivingService.receive()가 하는 일을 그대로 흉내 낸다.
--
--   ▣ 검수 1건이 남기는 것 (ReceivingService.receiveLine)
--     1. lot       배치 재사용 키(상품 + 입고일자 + 제조일자)로 찾고, 없으면 채번
--     2. ib_line   rcvd_qty += 검수수량 (양품만 — 불량 rjct_qty는 반품입고 전용이라 만들지 않는다)
--     3. inv       (상품, RCV-STAGE, Lot) on_hand_qty += 검수수량
--     4. inv_hist  RECEIVE +검수수량. 근거는 입고번호 + ib_line_id
--     5. ib_order  SCHEDULED → RECEIVING
--
--   ▣ 적치지시는 여기서 만들지 않는다. 일부러 그렇다.
--     적치지시는 RCV-STAGE 재고의 예약(inv.aloc_qty)과 한 몸이다 — 발행이 invStore.reserve로
--     예약을 걸고 실행·취소가 그것을 소진하거나 되돌린다(PutawayTaskService). 지시 행만 손으로
--     넣으면 예약 없는 지시가 되어 실행할 때 「적치지시가 예약한 재고가 없습니다 (정합성 오류)」로
--     막힌다. 발행은 「적치지시」 화면에서 할 것 — 거기서 예약이 정상으로 걸린다.
--
--   ▣ 3과 4는 한 몸이다. 「inv_hist 합계 = inv 스냅샷」이 이 스키마의 핵심 불변식이라,
--     둘 중 하나만 넣으면 재고가 조용히 어긋난다. 아래 검증 쿼리 1이 그걸 확인한다.
--
--   ▣ 되돌릴 수 있다. 이 시드가 만든 행은 created_by = 'seed-rcv'로 표시하고,
--     docs/seed-dev-receiving-undo.sql 이 그 표시를 따라 재고·라인·헤더를 원상복구한다.
--     append-only 원장인 inv_hist가 표시의 주인이고 나머지는 거기서 역산한다.
--
--   ▣ 재실행 안전. 이미 검수가 시작된 입고건(rcvd_qty 또는 rjct_qty > 0)과 SCHEDULED가 아닌
--     입고건은 건너뛴다 — 두 번 돌려도 재고가 두 배가 되지 않고, 화면에서 만든 데이터도 안 건드린다.
--
--   전제: docs/schema.sql → docs/seed-dev.sql 이 적용돼 있고, SCHEDULED 입고건이 남아 있을 것.
--         (남은 게 없으면 아무것도 안 하고 NOTICE만 찍는다 — 그때는 발주·확정부터 만들어야 한다)
--
--   실행(DBeaver): 이 파일을 열고 Alt+X. NOTICE는 결과 패널의 Server Output 탭에서 볼 것.
--   전체가 DO 블록 하나다 (BEGIN;/COMMIT;을 쓰지 않는다) — CLAUDE.md 규칙.
--   DO 블록은 통째로 원자적이라 중간에 실패하면 아무것도 반영되지 않는다.
-- =====================================================================

DO $seed_rcv$
DECLARE
    -- 이 입고건만 절반만 검수해 「검수 진행 중(미검수 잔량 있음)」 상태를 하나 남긴다.
    -- 없는 번호를 적어도 무해하다 — 그러면 대상 전건이 전량 검수된다.
    c_partial_ib_no CONSTANT TEXT := 'IB-20260719-001';
    c_marker        CONSTANT TEXT := 'seed-rcv';   -- 되돌리기가 따라가는 표시

    v_stage      BIGINT;
    v_order      RECORD;
    v_line       RECORD;
    v_receipt_de DATE;
    v_mfg_de     DATE;
    v_expiry_de  DATE;
    v_lot_id     BIGINT;
    v_lot_no     TEXT;
    v_seq        INT;
    v_qty        BIGINT;
    v_orders     INT := 0;
    v_lines      INT := 0;
    v_total      BIGINT := 0;
BEGIN
    SELECT loc_id INTO v_stage FROM loc WHERE loc_cd = 'RCV-STAGE';
    IF v_stage IS NULL THEN
        RAISE EXCEPTION '입고 스테이징 로케이션(RCV-STAGE)이 없다 — docs/seed-dev.sql을 먼저 적용할 것';
    END IF;

    FOR v_order IN
        SELECT o.ib_order_id, o.ib_no, o.expct_de
        FROM ib_order o
        WHERE o.status = 'SCHEDULED'
          AND EXISTS (SELECT 1 FROM ib_line l WHERE l.ib_order_id = o.ib_order_id)
          AND NOT EXISTS (
              SELECT 1 FROM ib_line l
              WHERE l.ib_order_id = o.ib_order_id AND (l.rcvd_qty > 0 OR l.rjct_qty > 0)
          )
        ORDER BY o.ib_order_id
    LOOP
        -- 입고일자는 **오늘**이다. ReceivingService가 입력이 없을 때 쓰는 기본값(LocalDate.now())과 같다 —
        -- 화면에서 오늘 검수한 것과 구분되지 않아야 한다. 예정일(대개 과거)을 쓰면 두 가지가 어긋난다:
        -- ① 적치지시 화면의 기본 검색이 「최근 7일」인데 그 조건이 Lot 입고일자 기준이라 시드가 안 보이고,
        -- ② 유통기한이 제조일 + shelf_life_days라 이미 지난 재고가 만들어진다.
        v_receipt_de := CURRENT_DATE;

        FOR v_line IN
            SELECT l.ib_line_id, l.prod_id, l.expct_qty, p.shelf_life_days
            FROM ib_line l
            JOIN prod p ON p.prod_id = l.prod_id
            WHERE l.ib_order_id = v_order.ib_order_id
            ORDER BY l.ib_line_id
        LOOP
            IF v_order.ib_no = c_partial_ib_no THEN
                v_qty := GREATEST(1, v_line.expct_qty / 2);   -- 나머지는 미검수 잔량으로 남는다
            ELSE
                v_qty := v_line.expct_qty;
            END IF;

            -- 유통기한 미관리 상품(shelf_life_days NULL)은 두 날짜를 모두 비운다 — LotIssuer.findOrCreate와 같다
            IF v_line.shelf_life_days IS NULL THEN
                v_mfg_de    := NULL;
                v_expiry_de := NULL;
            ELSE
                v_mfg_de    := v_receipt_de - 1;
                v_expiry_de := v_mfg_de + v_line.shelf_life_days;
            END IF;

            -- ① Lot — 배치 재사용 키로 먼저 찾는다. 같은 날 같은 상품이 두 입고건에 걸쳐 들어오면
            --    Lot 하나를 공유한다(LotIssuer.find와 같은 판정). NULL끼리도 매치돼야 하므로
            --    = 이 아니라 IS NOT DISTINCT FROM을 쓴다 — 유통기한 미관리 상품이 여기 걸린다
            SELECT lot_id INTO v_lot_id
            FROM lot
            WHERE prod_id = v_line.prod_id
              AND receipt_dt = v_receipt_de
              AND mfg_dt IS NOT DISTINCT FROM v_mfg_de
            ORDER BY lot_id
            LIMIT 1;

            IF v_lot_id IS NULL THEN
                -- 채번은 LotIssuer.nextLotNo와 같은 식: LOT-{입고일자 yyMMdd}-{상품별·일자별 순번}.
                -- 「건수 + 1」이라 이 시드가 만든 Lot도 다음 채번에 그대로 반영된다(nbr_seq를 쓰지 않는다)
                SELECT count(*) + 1 INTO v_seq
                FROM lot
                WHERE prod_id = v_line.prod_id AND receipt_dt = v_receipt_de;

                v_lot_no := 'LOT-' || to_char(v_receipt_de, 'YYMMDD') || '-' || lpad(v_seq::text, 3, '0');

                INSERT INTO lot (prod_id, lot_no, receipt_dt, mfg_dt, expiry_dt, created_by)
                VALUES (v_line.prod_id, v_lot_no, v_receipt_de, v_mfg_de, v_expiry_de, c_marker)
                RETURNING lot_id INTO v_lot_id;
            END IF;

            -- ② 라인 누계
            UPDATE ib_line
            SET rcvd_qty = rcvd_qty + v_qty, updated_at = CURRENT_TIMESTAMP, updated_by = c_marker
            WHERE ib_line_id = v_line.ib_line_id;

            -- ③ 재고 스냅샷 — 입고 스테이징에 쌓는다. 적치가 여기서 보관 로케이션으로 옮긴다
            INSERT INTO inv (prod_id, loc_id, lot_id, on_hand_qty, created_by)
            VALUES (v_line.prod_id, v_stage, v_lot_id, v_qty, c_marker)
            ON CONFLICT ON CONSTRAINT uq_inv DO UPDATE
            SET on_hand_qty = inv.on_hand_qty + EXCLUDED.on_hand_qty,
                updated_at = CURRENT_TIMESTAMP, updated_by = c_marker;

            -- ④ 이력 — ③과 짝이다. RECEIVE는 실물 이동이 아니라 유입이므로 from_loc_id/to_loc_id는
            --    비운다(InvStore.increase가 그 둘에 null을 넘긴다). created_by가 되돌리기의 근거다
            INSERT INTO inv_hist (tx_typ, prod_id, loc_id, lot_id, qty,
                                  rfn_doc_typ, rfn_doc_no, ib_line_id, created_by)
            VALUES ('RECEIVE', v_line.prod_id, v_stage, v_lot_id, v_qty,
                    'INBOUND', v_order.ib_no, v_line.ib_line_id, c_marker);

            v_lines := v_lines + 1;
            v_total := v_total + v_qty;
        END LOOP;

        -- ⑤ 헤더 — 첫 검수에서 SCHEDULED → RECEIVING. 입고확정(CONFIRMED)으로 미리 넘기지 않는다:
        --    확정은 적치가 끝난 뒤(적치 = 검수) 화면의 [입고확정]이 하는 일이고,
        --    여기서 넘겨 버리면 정작 적치할 것이 없어진다
        UPDATE ib_order
        SET status = 'RECEIVING', updated_at = CURRENT_TIMESTAMP, updated_by = c_marker
        WHERE ib_order_id = v_order.ib_order_id;

        v_orders := v_orders + 1;
    END LOOP;

    IF v_orders = 0 THEN
        RAISE NOTICE '검수할 입고예정이 없다 — 대상은 「SCHEDULED이면서 아직 검수 이력이 없는」 입고건이다.';
        RAISE NOTICE '남은 게 없으면 발주(입고주문) → 주문확정으로 새 ASN을 먼저 만들 것.';
    ELSE
        RAISE NOTICE '검수 완료 — 입고건 %건 / 라인 %건 / 총 % EA를 RCV-STAGE에 적재했다.',
            v_orders, v_lines, v_total;
        RAISE NOTICE '다음: 「적치지시」 화면에서 발행 → 「적치」 화면 [맵] 탭에서 끌어다 놓기.';
    END IF;
END
$seed_rcv$;


-- =====================================================================
-- 검증 — 위 블록을 돌린 뒤 따로 실행한다 (블록 밖이라 주석을 풀고 쓰면 된다).
-- =====================================================================

-- 1) 핵심 불변식: inv_hist 합계 = inv 스냅샷. **0행이어야 정상이다.**
--
-- SELECT COALESCE(i.prod_id, h.prod_id) AS prod_id,
--        COALESCE(i.loc_id,  h.loc_id)  AS loc_id,
--        COALESCE(i.lot_id,  h.lot_id)  AS lot_id,
--        i.on_hand_qty AS snapshot_qty, h.hist_qty
-- FROM inv i
-- FULL JOIN (
--     SELECT prod_id, loc_id, lot_id, SUM(qty) AS hist_qty
--     FROM inv_hist GROUP BY prod_id, loc_id, lot_id HAVING SUM(qty) <> 0
-- ) h ON h.prod_id = i.prod_id AND h.loc_id = i.loc_id AND h.lot_id = i.lot_id
-- WHERE COALESCE(i.on_hand_qty, -1) IS DISTINCT FROM COALESCE(h.hist_qty, -1);

-- 2) 적치 대기 물량 — 「적치지시」 화면에 뜰 것들
--
-- 라인은 inv_hist.ib_line_id를 타고 찾는다 — 상품으로 이으면 같은 상품의 다른 입고건까지 붙어 뻥튀기된다
--
-- SELECT o.ib_no, o.status, p.prod_cd, p.prod_nm, p.tmp_zon,
--        l.lot_no, l.expiry_dt, il.expct_qty, il.rcvd_qty, il.ptawy_qty,
--        i.on_hand_qty, i.aloc_qty
-- FROM inv_hist h
-- JOIN loc  lc ON lc.loc_id = h.loc_id AND lc.loc_cd = 'RCV-STAGE'
-- JOIN inv  i  ON i.prod_id = h.prod_id AND i.loc_id = h.loc_id AND i.lot_id = h.lot_id
-- JOIN prod p  ON p.prod_id = h.prod_id
-- JOIN lot  l  ON l.lot_id  = h.lot_id
-- JOIN ib_line  il ON il.ib_line_id = h.ib_line_id
-- JOIN ib_order o  ON o.ib_order_id = il.ib_order_id
-- WHERE h.tx_typ = 'RECEIVE' AND il.rcvd_qty > il.ptawy_qty
-- ORDER BY o.ib_no, p.prod_cd;


-- =====================================================================
-- 되돌리기 — 이 시드가 만든 것만 걷어낸다 (created_by = 'seed-rcv' 표시를 따라간다).
--
--   ▶ docs/seed-dev-receiving-undo.sql  — 그 파일을 열어 Alt+X.
--
-- 같은 SQL을 두 곳에 두지 않는다(한쪽만 고치면 조용히 갈라진다). 별도 파일인 이유는 하나 더 있다 —
-- DO 블록은 첫 줄부터 끝까지가 한 문장이라 조각을 복사해 붙이면 여는 줄이 빠지기 쉽고,
-- 그러면 DECLARE가 홀로 실행돼 「syntax error at or near "CONSTANT"」가 난다.
-- =====================================================================
