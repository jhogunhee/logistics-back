-- =====================================================================
-- 기존 FK 제약 전체 제거 (PostgreSQL / Supabase)
--   방침 변경: 참조 무결성은 애플리케이션이 보증하고 DB FK는 걸지 않는다
--   (docs/schema.sql 헤더 참고 — 서비스/DB 분리 유연성, 마이그레이션 순서 제약 축소가 목적).
--
--   이 스크립트는 "이미 라이브에 반영된" FK만 대상으로 한다.
--   vendor/oms_ib_order/oms_ib_line/putaway_task 관련 FK는 아직 라이브에 없으므로
--   (migration-oms-inbound.sql, migration-inbound-putaway.sql이 이미 FK 없이 수정됨) 여기 대상이 아니다.
--
--   실행: Supabase 대시보드 › SQL Editor 에 붙여넣고 Run
-- =====================================================================

BEGIN;

ALTER TABLE lot        DROP CONSTRAINT IF EXISTS fk_lot_sku;

ALTER TABLE inv         DROP CONSTRAINT IF EXISTS fk_inv_sku;
ALTER TABLE inv         DROP CONSTRAINT IF EXISTS fk_inv_loc;
ALTER TABLE inv         DROP CONSTRAINT IF EXISTS fk_inv_lot;

ALTER TABLE inv_hist    DROP CONSTRAINT IF EXISTS fk_invh_sku;
ALTER TABLE inv_hist    DROP CONSTRAINT IF EXISTS fk_invh_loc;
ALTER TABLE inv_hist    DROP CONSTRAINT IF EXISTS fk_invh_lot;

ALTER TABLE ib_line     DROP CONSTRAINT IF EXISTS fk_ib_line_order;
ALTER TABLE ib_line     DROP CONSTRAINT IF EXISTS fk_ib_line_sku;

ALTER TABLE outb_order  DROP CONSTRAINT IF EXISTS fk_outb_order_store;
ALTER TABLE outb_order  DROP CONSTRAINT IF EXISTS fk_outb_order_wave;

ALTER TABLE outb_line   DROP CONSTRAINT IF EXISTS fk_outb_line_order;
ALTER TABLE outb_line   DROP CONSTRAINT IF EXISTS fk_outb_line_sku;

ALTER TABLE outb_alloc  DROP CONSTRAINT IF EXISTS fk_alloc_line;
ALTER TABLE outb_alloc  DROP CONSTRAINT IF EXISTS fk_alloc_inv;

COMMIT;

-- 검증: 남아있는 FK가 없어야 한다 (0건)
--   SELECT conname, conrelid::regclass FROM pg_constraint WHERE contype = 'f';
