-- =====================================================================
-- 증분 마이그레이션: 라이브 DB를 schema.sql 상태로 복구 (입고 도메인)
--   배경: 2026-08-02 전수조사(스크래치 DB에 schema.sql 적용 후 카탈로그 diff)에서
--   라이브 DB가 이 레포 밖에서 변경된 것을 확인했다 —
--     · 삭제됨: ib_line.rcvd_qty / ib_line.ptawy_qty / ck_ib_line_qty / ib_order.clos_dt
--       → IbLine·IbOrder 엔티티가 이 컬럼을 매핑하므로 입고 관련 조회 전부가 SQL 오류 (오류의 원인)
--     · 추가됨(레포 밖 산출물): ib_hist · ib_receive_task · ib_putaway_task 테이블,
--       ib_line.confirm_qty/confirmed_at/confirmed_by 컬럼
--       — text/timestamptz/now()/자동 시퀀스 등 이 레포 컨벤션(varchar/timestamp/IDENTITY/네이밍 사전)과
--         전부 다르다. 조사 시점에 모두 0행/전부 NULL이라 제거해도 데이터 손실이 없다.
--
--   복구 내용:
--     1. 삭제된 컬럼·CHECK 복원
--     2. rcvd_qty/ptawy_qty 백필 — inv_hist 원장에서 재계산 ("이력 합계 = 스냅샷" 불변식의 응용).
--        재실행해도 원장 기준으로 다시 계산될 뿐이라 안전하다.
--        ib_order.clos_dt 값은 원장에 없어 복구 불가 — NULL로 남는다 (마감 시각 소실).
--     3. 레포 밖 산출물 제거 (0행 확인 후의 DROP — 그래도 실행 전에 아래 카운트로 재확인할 것)
--        SELECT count(*) FROM ib_hist; SELECT count(*) FROM ib_receive_task;
--        SELECT count(*) FROM ib_putaway_task;
--        SELECT count(*) FROM ib_line WHERE confirm_qty IS NOT NULL;
--
--   재실행 안전: IF NOT EXISTS / IF EXISTS. BEGIN/COMMIT 없이 DO 블록 하나 (DBeaver 25P02 방지).
-- =====================================================================

DO $mig$
BEGIN

    -- 1. 삭제된 컬럼·제약 복원 (schema.sql 정의 그대로)
    ALTER TABLE ib_line ADD COLUMN IF NOT EXISTS rcvd_qty  BIGINT DEFAULT 0 NOT NULL;
    ALTER TABLE ib_line ADD COLUMN IF NOT EXISTS ptawy_qty BIGINT DEFAULT 0 NOT NULL;
    ALTER TABLE ib_order ADD COLUMN IF NOT EXISTS clos_dt TIMESTAMP;

    -- 2. 원장(inv_hist)에서 수량 누계 백필
    --    rcvd_qty  = RECEIVE(+) 와 검수취소 ADJUST(-) 합 (둘 다 ib_line_id를 갖는다)
    --    ptawy_qty = 적치 MOVE의 도착지(+) 행 합
    UPDATE ib_line l
    SET rcvd_qty = coalesce((SELECT sum(h.qty) FROM inv_hist h
                             WHERE h.ib_line_id = l.ib_line_id
                               AND h.tx_typ IN ('RECEIVE', 'ADJUST')), 0),
        ptawy_qty = coalesce((SELECT sum(h.qty) FROM inv_hist h
                              WHERE h.ib_line_id = l.ib_line_id
                                AND h.tx_typ = 'MOVE' AND h.qty > 0), 0);

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_ib_line_qty') THEN
        ALTER TABLE ib_line ADD CONSTRAINT ck_ib_line_qty CHECK (
            expct_qty > 0 AND rcvd_qty >= 0
            AND ptawy_qty >= 0 AND ptawy_qty <= rcvd_qty
        );
    END IF;

    -- 3. 레포 밖 산출물 제거 (2026-08-02 조사 시점 전부 0행 / 전부 NULL)
    DROP TABLE IF EXISTS ib_hist;
    DROP TABLE IF EXISTS ib_receive_task;
    DROP TABLE IF EXISTS ib_putaway_task;
    ALTER TABLE ib_line DROP COLUMN IF EXISTS confirm_qty;
    ALTER TABLE ib_line DROP COLUMN IF EXISTS confirmed_at;
    ALTER TABLE ib_line DROP COLUMN IF EXISTS confirmed_by;

END
$mig$;
