-- ASN 취소 상태 폐지 (2026-07-31)
--   확정취소가 ASN을 CANCELLED로 남기던 방식 → 행 삭제 방식으로 전환.
--   1) 남아 있는 CANCELLED ASN(헤더+라인) 삭제
--   2) ck_ib_order_status에서 CANCELLED 제거
--   3) uq_ib_order_oms_active 부분 유니크 → 평범한 유니크로 교체
--   DBeaver에서 실행. 재실행 안전.
DO $mig$
BEGIN
    -- 1) 취소된 ASN 삭제. 취소는 검수 시작 전(SCHEDULED)에만 가능했으므로
    --    inv_hist 등 하위 참조가 있을 수 없다 — 라인부터 지우고 헤더를 지운다.
    DELETE FROM ib_line
    WHERE ib_order_id IN (SELECT ib_order_id FROM ib_order WHERE status = 'CANCELLED');
    DELETE FROM ib_order WHERE status = 'CANCELLED';

    -- 2) 상태 CHECK에서 CANCELLED 제거
    ALTER TABLE ib_order DROP CONSTRAINT IF EXISTS ck_ib_order_status;
    ALTER TABLE ib_order ADD CONSTRAINT ck_ib_order_status
        CHECK (status IN ('SCHEDULED', 'RECEIVING', 'RECEIVED', 'COMPLETED'));

    -- 3) 부분 유니크(WHERE status <> 'CANCELLED') → 평범한 유니크
    DROP INDEX IF EXISTS uq_ib_order_oms_active;
    CREATE UNIQUE INDEX uq_ib_order_oms_active ON ib_order (oms_ib_order_id);
END
$mig$;
