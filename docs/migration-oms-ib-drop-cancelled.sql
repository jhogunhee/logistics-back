-- =====================================================================
-- 입고주문 상태 정리 — 취소(CANCELLED) 제거 + CONVERTED → CONFIRMED 개명
--   전제: docs/migration-oms-ib-fields.sql 까지 적용된 DB.
--
--   배경 —
--   입고주문이 CREATED / CONVERTED / CANCELLED 셋을 갖고, 화면에는 「주문취소」와
--   「변환취소」가 나란히 있었다. 사용자가 기대한 조작은 삭제 · 취소 · 확정이었는데,
--   실제로 겹치는 건 "없앤다" 하나인데 표현이 둘(삭제 없음 + 취소 상태)로 갈려 있었다.
--
--   ▣ 취소 상태를 두지 않고 삭제로 간다.
--     - 확정 전(CREATED)  : 바로 삭제. 라인은 cascade + orphanRemoval 이 함께 지운다.
--     - 확정 후(CONFIRMED): 확정취소로 CREATED 로 되돌린 뒤 삭제.
--     "지운 것도 아니고 쓰는 것도 아닌" 상태를 두면 목록·집계마다 그 상태를 빼는 필터가
--     따라붙고, 한 군데라도 빠지면 취소된 주문이 화면에 되살아난다. 마스터에서 us_yn 을
--     걷어낸 것과 같은 판단이다 (docs/migration-drop-us-yn.sql).
--
--   ▣ CONVERTED → CONFIRMED, converted_at → cfm_dt.
--     사용자가 하는 행위는 「발주 확정」이고 ASN 생성은 그 결과다. 상태값이 결과(변환)를
--     가리키고 있어 화면 용어와 계속 어긋났다.
--     「변환」은 docs/naming-dictionary.md 에 없는 단어이기도 했다 — CNVR 은 「환산」(단위
--     환산)이라 오히려 헷갈린다. 사전에 이미 있는 확정 = CFM 을 쓴다 (일시는 DT).
--
--   ▣ ASN(ib_order)의 CANCELLED 는 건드리지 않는다.
--     확정취소해도 "예정이 나갔다가 물렸다"는 흔적은 남아야 하고,
--     uq_ib_order_active 부분 인덱스가 그 값으로 유효한 ASN 하나를 강제한다.
--
--   ▣ 되돌릴 수 없는 삭제가 하나 있다.
--     지금 CANCELLED 인 주문이 있으면 그 행과 라인이 사라진다. 1단계가 건수를 NOTICE 로
--     알려주므로, 남겨야 할 주문이면 스크립트를 돌리기 전에 상태를 CREATED 로 돌려놓을 것.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $oms_status$
DECLARE
    n int;
BEGIN
    -- 1. 취소 주문 정리 -------------------------------------------------
    SELECT count(*) INTO n FROM oms_ib_order WHERE status = 'CANCELLED';
    IF n > 0 THEN
        RAISE NOTICE '취소 상태 주문 % 건을 라인과 함께 삭제한다', n;
        DELETE FROM oms_ib_line
         WHERE oms_ib_order_id IN (SELECT oms_ib_order_id FROM oms_ib_order WHERE status = 'CANCELLED');
        DELETE FROM oms_ib_order WHERE status = 'CANCELLED';
    ELSE
        RAISE NOTICE '취소 상태 주문 없음 — 건너뜀';
    END IF;

    -- 2. CHECK 를 먼저 떼어낸다 -----------------------------------------
    -- 제약이 걸린 채로 값을 바꾸면 CONFIRMED 가 허용 목록에 없어 UPDATE 가 실패한다.
    ALTER TABLE oms_ib_order DROP CONSTRAINT IF EXISTS ck_oms_ib_order_status;

    -- 3. 상태값 개명 ----------------------------------------------------
    UPDATE oms_ib_order SET status = 'CONFIRMED' WHERE status = 'CONVERTED';
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'CONVERTED → CONFIRMED % 건 개명', n;

    -- 4. 확정 시각 컬럼 개명 ---------------------------------------------
    -- 「변환」이 사전에 없는 단어라 사전대로 다시 짓는다: 확정 CFM + 일시 DT.
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'oms_ib_order' AND column_name = 'converted_at') THEN
        ALTER TABLE oms_ib_order RENAME COLUMN converted_at TO cfm_dt;
        RAISE NOTICE 'oms_ib_order.converted_at → cfm_dt';
    ELSE
        RAISE NOTICE 'oms_ib_order.cfm_dt 이미 적용됨 — 건너뜀';
    END IF;

    -- 5. CHECK 재정의 ---------------------------------------------------
    ALTER TABLE oms_ib_order
        ADD CONSTRAINT ck_oms_ib_order_status CHECK (status IN ('CREATED', 'CONFIRMED'));
    RAISE NOTICE 'ck_oms_ib_order_status 재정의 (CREATED · CONFIRMED)';

    COMMENT ON COLUMN oms_ib_order.status IS 'CREATED 작성 / CONFIRMED 확정(=ASN 생성됨). 확정취소하면 CREATED로 돌아와 재확정할 수 있다. 취소 상태는 두지 않는다 — 없앨 주문은 삭제한다(확정 전만)';
    COMMENT ON COLUMN oms_ib_order.cfm_dt IS '확정(ASN 생성) 시각. 확정취소하면 다시 NULL이 된다';

    -- 6. 검증 -----------------------------------------------------------
    SELECT count(*) INTO n FROM oms_ib_order WHERE status NOT IN ('CREATED', 'CONFIRMED');
    IF n > 0 THEN
        RAISE EXCEPTION '허용되지 않는 상태의 주문이 % 건 남아 있다.', n;
    END IF;

    SELECT count(*) INTO n FROM oms_ib_order WHERE status = 'CONFIRMED' AND cfm_dt IS NULL;
    IF n > 0 THEN
        RAISE NOTICE '주의 — 확정 상태인데 확정시각이 비어 있는 주문이 % 건 있다', n;
    END IF;

    RAISE NOTICE '입고주문 상태 정리 완료';
END
$oms_status$;

-- =====================================================================
-- 적용 후 확인
--   1) 상태 분포 (CREATED · CONFIRMED 만 나와야 한다)
--      SELECT status, count(*) FROM oms_ib_order GROUP BY status;
--   2) 컬럼 개명 확인 (cfm_dt 1건 / converted_at 0건)
--      SELECT column_name FROM information_schema.columns
--       WHERE table_name = 'oms_ib_order' AND column_name IN ('cfm_dt', 'converted_at');
--   3) 고아 라인 0건
--      SELECT count(*) FROM oms_ib_line l
--       WHERE NOT EXISTS (SELECT 1 FROM oms_ib_order o WHERE o.oms_ib_order_id = l.oms_ib_order_id);
--   4) ASN 쪽 CANCELLED 는 그대로 남아 있어야 한다 (확정취소 흔적)
--      SELECT status, count(*) FROM ib_order GROUP BY status;
-- =====================================================================
