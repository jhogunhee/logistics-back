-- 출고주문 취소 상태 폐지 (2026-08-03)
--   전제: docs/migration-oms-outb.sql 까지 적용된 DB (outb_order.oms_outb_order_id 가 있어야 한다).
--
--   배경 —
--   같은 「없앤다」를 두 조작이 서로 다르게 처리하고 있었다. 창고 쪽 취소는 행을 CANCELLED 로
--   남기고 상위 주문을 확정으로 두었고, OMS 확정취소는 행을 지우고 주문을 작성으로 되돌렸다.
--   화면에서는 무엇을 눌러야 하는지가 매번 애매했고 목록·집계에는 취소분을 빼는 필터가 붙는다.
--   출고 화면프로세스 정의서(레포 밖 참고자료) §1 도 「취소는 웨이브 생성 이전만 가능,
--   처리는 DELETE」로 정의한다 — 상태를 남기는 쪽이 정의서와도 어긋나 있었다.
--   입고예정(ASN)이 같은 이유로 CANCELLED 를 폐지한 방식을 그대로 따른다
--   (docs/migration-drop-asn-cancelled.sql).
--
--   1) 남아 있는 CANCELLED 출고주문의 상위 OMS 주문을 작성(CREATED)으로 되돌린다
--   2) 그 출고주문(헤더+라인) 삭제
--   3) ck_outb_order_status 에서 CANCELLED 제거
--
--   DBeaver 에서 실행. 전체가 DO 블록 하나이고 재실행 안전하다.
-- =====================================================================

DO $mig$
DECLARE
    n int;
BEGIN
    -- 1) 상위 주문 원복. 취소된 창고 문서를 그냥 지우면 주문은 '확정'인데 창고엔 아무것도 없는
    --    상태로 고착된다 — 확정취소가 하는 일(행 삭제 + 주문 작성 복귀)과 결과를 같게 맞춘다.
    UPDATE oms_outb_order m
       SET status = 'CREATED', cfm_dt = NULL, updated_at = CURRENT_TIMESTAMP
      FROM outb_order o
     WHERE o.oms_outb_order_id = m.oms_outb_order_id
       AND o.status = 'CANCELLED';

    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE '취소분의 상위 출고주문 % 건을 작성 상태로 원복', n;

    -- 2) 취소된 출고주문 삭제. 취소는 할당 전(CREATED)에만 가능했으므로 할당 레코드가 있을 수
    --    없지만, 남아 있다면 지울 행을 가리키는 미아가 되므로 함께 지운다.
    DELETE FROM outb_alloc
     WHERE outb_line_id IN (
         SELECT l.outb_line_id FROM outb_line l
           JOIN outb_order o ON o.outb_order_id = l.outb_order_id
          WHERE o.status = 'CANCELLED');

    DELETE FROM outb_line
     WHERE outb_order_id IN (SELECT outb_order_id FROM outb_order WHERE status = 'CANCELLED');

    DELETE FROM outb_order WHERE status = 'CANCELLED';

    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE '취소 상태 출고주문 % 건 삭제', n;

    -- 3) 상태 CHECK 에서 CANCELLED 제거
    ALTER TABLE outb_order DROP CONSTRAINT IF EXISTS ck_outb_order_status;
    ALTER TABLE outb_order ADD CONSTRAINT ck_outb_order_status
        CHECK (status IN ('CREATED', 'ALLOCATED', 'PICKING', 'PICKED', 'SHIPPED'));

    COMMENT ON COLUMN outb_order.status IS 'CREATED 생성 / ALLOCATED 할당 / PICKING 피킹중 / PICKED 피킹완료 / SHIPPED 출고확정. 취소 상태 없음 — 없앨 주문은 OMS 확정취소가 행을 삭제한다(웨이브 편성 전만). 같은 「없앤다」를 두 조작이 다르게 처리하던 겹침을 정리한 것이다 — migration-drop-outb-cancelled.sql';

    RAISE NOTICE '출고주문 취소 상태 폐지 완료';
END
$mig$;

-- =====================================================================
-- 적용 후 확인
--   SELECT status, count(*) FROM outb_order GROUP BY status ORDER BY status;
--   SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_outb_order_status';
-- =====================================================================
