-- =====================================================================
-- 입고 상태 모델 개편 — ib_order.status 4값 → 3값 (SCHEDULED → RECEIVING → CONFIRMED)
--   전제: docs/migration-ib-clos-to-cfm.sql 까지 적용된 DB (cfm_dt 컬럼 존재).
--
--   배경 —
--   입고확정을 「온 것은 전부 적치 완료된 뒤, 사람이 눌러 결품(예정-검수)을
--   못박으며 입고건을 닫는 유일한 종결 액션」으로 재정의했다. 그에 따라:
--     · 전량 검수 시 RECEIVED 자동 전이 제거 (checkAndAutoReceive 삭제)
--     · 전량 적치 시 COMPLETED 자동 전이 제거 (checkAndComplete 삭제)
--     · POST /inbound/asns/{id}/close → /confirm (전제: 전 라인 ptawy_qty = rcvd_qty)
--   적치지시/적치완료 등 진행 단계는 상태로 저장하지 않고 수량·지시에서 파생한다
--   (IbPrgr — 「상태와 수량의 분담」 원칙). migration-ib-clos-to-cfm.sql 머리말이
--   "별개 작업"으로 분리해 뒀던 그 후속 작업이 이것이다.
--
--   ▣ 기존 행 매핑 —
--     RECEIVED  → RECEIVING + cfm_dt NULL
--       옛 RECEIVED는 대부분 전량 검수 「자동」 전이가 만든 것이라 사람의 확정이
--       아니고, 새 모델의 CONFIRMED 전제(전량 적치)도 충족하는지 알 수 없다.
--       진행 중으로 되돌리고 확정은 화면에서 다시 누르게 한다. (데모 데이터라 수용)
--     COMPLETED → CONFIRMED (cfm_dt 유지)
--       전량 적치까지 끝난 행이라 새 CONFIRMED의 전제를 이미 충족한다.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     재실행 가드: CHECK 제약 정의에 CONFIRMED가 이미 있으면 전체를 건너뛴다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $ib_status3$
DECLARE
    n int;
BEGIN
    -- 0. 재실행 가드 — 제약이 이미 3값(CONFIRMED 포함)이면 적용 완료 상태다
    IF EXISTS (SELECT 1 FROM pg_constraint
                WHERE conname = 'ck_ib_order_status'
                  AND pg_get_constraintdef(oid) LIKE '%CONFIRMED%') THEN
        RAISE NOTICE '입고 상태 3값 이미 적용됨 — 건너뜀';
        RETURN;
    END IF;

    -- 1. 옛 CHECK 제거 (값 매핑 전에 풀어야 UPDATE가 통과한다)
    ALTER TABLE ib_order DROP CONSTRAINT IF EXISTS ck_ib_order_status;

    -- 2. 기존 행 매핑
    UPDATE ib_order SET status = 'RECEIVING', cfm_dt = NULL WHERE status = 'RECEIVED';
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'RECEIVED → RECEIVING (확정 되돌림, cfm_dt 제거): % 건', n;

    UPDATE ib_order SET status = 'CONFIRMED' WHERE status = 'COMPLETED';
    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'COMPLETED → CONFIRMED (cfm_dt 유지): % 건', n;

    -- 3. 새 CHECK
    ALTER TABLE ib_order ADD CONSTRAINT ck_ib_order_status
        CHECK (status IN ('SCHEDULED', 'RECEIVING', 'CONFIRMED'));

    -- 4. 주석 재설정 (docs/schema.sql과 동일 문구)
    COMMENT ON COLUMN ib_order.status IS 'SCHEDULED 예정 / RECEIVING 입고중 / CONFIRMED 입고확정. 자동 전이 없음 — CONFIRMED는 입고확정 버튼만이 만든다(전제: 검수분 전량 적치). 적치지시/적치완료 등 진행 단계는 저장하지 않고 파생(IbPrgr). 취소 상태 없음 — 확정취소는 행을 삭제한다(검수 시작 전만)';
    COMMENT ON COLUMN ib_order.cfm_dt IS '입고확정 시각 — 사람이 입고확정 버튼을 누른 시각(IbOrder.confirm만 채운다). 이 시점에 결품(예정-검수)이 못박힌다. oms_ib_order.cfm_dt(발주 확정 = ASN 생성)와는 다른 사건이다';

    -- 5. 검증 — 상태와 확정시각의 동행 (둘 다 0건이어야 정상)
    SELECT count(*) INTO n FROM ib_order WHERE status = 'CONFIRMED' AND cfm_dt IS NULL;
    IF n > 0 THEN
        RAISE NOTICE '주의 — CONFIRMED인데 확정시각이 비어 있는 입고건이 % 건 있다', n;
    END IF;

    SELECT count(*) INTO n FROM ib_order
     WHERE status IN ('SCHEDULED', 'RECEIVING') AND cfm_dt IS NOT NULL;
    IF n > 0 THEN
        RAISE NOTICE '주의 — 확정 전 상태인데 확정시각이 남아 있는 입고건이 % 건 있다', n;
    END IF;

    RAISE NOTICE '입고 상태 3값 개편 완료';
END
$ib_status3$;

-- =====================================================================
-- 적용 후 확인
--   1) 상태 분포 (RECEIVED / COMPLETED 는 0건이어야 한다)
--      SELECT status, count(*) FROM ib_order GROUP BY status ORDER BY status;
--   2) 제약 정의 확인 (3값)
--      SELECT pg_get_constraintdef(oid) FROM pg_constraint
--       WHERE conname = 'ck_ib_order_status';
--   3) 상태·확정시각 동행 (0건이어야 한다)
--      SELECT count(*) FROM ib_order
--       WHERE (status = 'CONFIRMED') <> (cfm_dt IS NOT NULL);
-- =====================================================================
