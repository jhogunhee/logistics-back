-- =====================================================================
-- inv_hist 의 입고라인 인덱스를 커버링으로 교체 — (ib_line_id) → (ib_line_id, tx_typ, created_at).
--
-- 현재 라이브 상태(migration-add-store-grp-typ.sql 적용) → schema.sql 상태.
-- 근거: 입고예정 목록의 「최종 검수일시」는 저장 컬럼이 아니라 원장에서 파생시킨다
--   (IbOrderRepositoryImpl.lastReceiveDt — RECEIVE 행의 max(created_at)).
--   파생을 유지하는 이유는 design.md 「시각의 원천 — 저장은 파생 불가능한 것만」 참고.
--
--   기존 ix_invh_ib_line 은 (ib_line_id) 한 컬럼이라, 인덱스로 찾은 행을 테이블에서 다시
--   읽어 tx_typ·created_at 을 확인해야 했다. inv_hist 는 모든 물리 변동이 쌓이는 원장이라
--   라인당 행이 계속 늘고(검수·취소·적치·이동), 그중 필요한 RECEIVE 몇 건을 고르려고
--   나머지까지 읽는 구조였다 — 목록을 열 때마다.
--   세 컬럼을 인덱스에 담으면 테이블을 읽지 않고(index-only), max(created_at)도
--   정렬돼 있어 구간의 끝만 보면 끝난다.
--
--   기존 인덱스는 지운다. 새 인덱스의 선두 컬럼이 ib_line_id 라 기존 인덱스가 답하던
--   질의(ib_line_id 단독 조회 — 검수 이력·적치 잔량 집계)를 그대로 답한다. 쓰기가 잦은
--   테이블이라 선두 컬럼이 같은 인덱스를 둘 유지할 이유가 없다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
--   - CREATE INDEX 가 그 사이 inv_hist 쓰기를 막는다. CONCURRENTLY 는 트랜잭션 밖에서만
--     동작해 DO 블록 안에서는 쓸 수 없다 — 행이 많아지면 아래 두 줄을 따로 실행할 것.
--       CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_invh_ib_line_typ_created ON inv_hist (ib_line_id, tx_typ, created_at);
--       DROP INDEX IF EXISTS ix_invh_ib_line;
--
-- 적용 후 확인:
--   EXPLAIN ANALYZE
--   SELECT ib_line_id, max(created_at) FROM inv_hist
--    WHERE ib_line_id IN (1,2,3) AND tx_typ = 'RECEIVE' GROUP BY ib_line_id;
--   → "Index Only Scan using ix_invh_ib_line_typ_created" 가 나오면 성공
-- =====================================================================
DO $mig$
BEGIN
    -- 1. 커버링 인덱스 생성 -------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM pg_indexes
                    WHERE tablename = 'inv_hist' AND indexname = 'ix_invh_ib_line_typ_created') THEN
        CREATE INDEX ix_invh_ib_line_typ_created ON inv_hist (ib_line_id, tx_typ, created_at);
        RAISE NOTICE 'ix_invh_ib_line_typ_created 생성 (ib_line_id, tx_typ, created_at)';
    ELSE
        RAISE NOTICE 'ix_invh_ib_line_typ_created 이미 존재 — 건너뜀';
    END IF;

    -- 2. 선두 컬럼이 같은 옛 인덱스 제거 -------------------------------------
    IF EXISTS (SELECT 1 FROM pg_indexes
                WHERE tablename = 'inv_hist' AND indexname = 'ix_invh_ib_line') THEN
        DROP INDEX ix_invh_ib_line;
        RAISE NOTICE 'ix_invh_ib_line 제거 — 새 인덱스가 같은 질의를 답한다';
    ELSE
        RAISE NOTICE 'ix_invh_ib_line 이미 없음 — 건너뜀';
    END IF;
END
$mig$;
