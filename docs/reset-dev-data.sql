-- 개발 DB 트랜잭션 데이터 리셋 — PostgreSQL / Supabase, DBeaver로 실행
--
-- 왜: UOM 정합이 어긋난 채(낱개 기준) 검수된 과거 데이터가 남아, 검수 화면을 입고단위 기준으로
--     바꾼 뒤 잔량이 소수점으로 보인다. 주문 원장부터 파생 재고까지 전부 비우고 새로 시작한다.
--
-- 지우는 것 (트랜잭션 데이터 전부 — 재고는 오직 검수로만 생기므로 입고를 지우면 전부 딸려 나간다):
--   주문 원장:  oms_ib_order/line, oms_outb_order/line
--   입고:       ib_order/line, putaway_task
--   출고:       outb_wave, outb_order/line, outb_alloc
--   재고:       inv, inv_hist, lot, inv_mov_task, inv_hld(+acrst/rlz_acrst), inv_stktk(+ln), lot_attr_chng
--   전략 실행로그: stgy_exec_log (참조하던 문서들이 사라지므로 함께 비운다)
--
-- 남기는 것: 마스터(prod, prod_uom, zon, loc, store, vendor, code_*, nbr_*)와
--   전략 정의(insp_plcy*, ptawy_stgy*, wav_stgy, aloc_stgy*, stgy_rvsn).
--   nbr_seq도 그대로 둔다 — 문서번호가 이어서 채번될 뿐 충돌하지 않는다.
--
-- 재실행 안전: DELETE만 쓰므로 몇 번을 돌려도 결과가 같다. DO 블록이라 전체가 한 트랜잭션이다.

DO $reset$
DECLARE
    n BIGINT;
BEGIN
    -- 출고 계열 (재고 할당이 걸려 있으므로 재고보다 먼저)
    DELETE FROM outb_alloc;        GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'outb_alloc: % 행 삭제', n;
    DELETE FROM outb_line;         GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'outb_line: % 행 삭제', n;
    DELETE FROM outb_order;        GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'outb_order: % 행 삭제', n;
    DELETE FROM outb_wave;         GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'outb_wave: % 행 삭제', n;
    DELETE FROM oms_outb_line;     GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'oms_outb_line: % 행 삭제', n;
    DELETE FROM oms_outb_order;    GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'oms_outb_order: % 행 삭제', n;

    -- 입고 계열
    DELETE FROM putaway_task;      GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'putaway_task: % 행 삭제', n;
    DELETE FROM ib_line;           GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'ib_line: % 행 삭제', n;
    DELETE FROM ib_order;          GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'ib_order: % 행 삭제', n;
    DELETE FROM oms_ib_line;       GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'oms_ib_line: % 행 삭제', n;
    DELETE FROM oms_ib_order;      GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'oms_ib_order: % 행 삭제', n;

    -- 재고 계열 (이동/홀드/실사/로트속성변경 → 이력 → 스냅샷 → Lot 순)
    DELETE FROM inv_mov_task;      GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'inv_mov_task: % 행 삭제', n;
    DELETE FROM inv_hld_rlz_acrst; GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'inv_hld_rlz_acrst: % 행 삭제', n;
    DELETE FROM inv_hld_acrst;     GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'inv_hld_acrst: % 행 삭제', n;
    DELETE FROM inv_hld;           GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'inv_hld: % 행 삭제', n;
    DELETE FROM inv_stktk_ln;      GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'inv_stktk_ln: % 행 삭제', n;
    DELETE FROM inv_stktk;         GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'inv_stktk: % 행 삭제', n;
    DELETE FROM lot_attr_chng;     GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'lot_attr_chng: % 행 삭제', n;
    DELETE FROM inv_hist;          GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'inv_hist: % 행 삭제', n;
    DELETE FROM inv;               GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'inv: % 행 삭제', n;
    DELETE FROM lot;               GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'lot: % 행 삭제', n;

    -- 전략 실행 로그 (정의·리비전은 남긴다)
    DELETE FROM stgy_exec_log;     GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'stgy_exec_log: % 행 삭제', n;

    RAISE NOTICE '리셋 완료 — 마스터와 전략 정의는 그대로다. 새 입고주문은 화면(OMS 입고주문 등록)에서 만들면 된다.';
END
$reset$;
