-- =====================================================================
-- 테이블 주석 간결화 — 44개 전부 「무슨 테이블인가」 한 줄로.
--
-- 배경: 테이블 주석이 총 4,300자(평균 98자, 최대 239자)까지 자라 DBeaver에서
--   목록을 훑을 때 오히려 안 읽혔다. 테이블 주석의 일은 「이게 뭔 테이블이냐」에
--   답하는 것이고, 「왜 그렇게 설계했나」의 자리는 docs/design.md 다(CLAUDE.md).
--   → 44개를 이름 + (구분되는 한 마디) 형태로 줄인다. 총 580자, 평균 13자, 최대 28자.
--
--   괄호를 붙인 것은 이름만으로 안 갈리는 짝들뿐이다 —
--     oms_ib_order/line ↔ ib_order/line,  oms_outb_order/line ↔ outb_order/line,
--     inv_hld / inv_hld_acrst / inv_hld_rlz_acrst,  inv_stktk / inv_stktk_ln,
--     lot_attr_chng(수량 변동 없음) ↔ inv_lot_chng(재고를 다른 Lot으로 이동)
--
--   잘려나간 근거는 잃지 않았다 — 넷은 docs/design.md 에 이미 있었고
--   (inv_hld 항등식 · inv_stktk_ln 미조사 NULL · outb_wave 2026-08-03 번복 ·
--    aloc_stgy 전략 0건 기본동작), 없던 inv_lot_chng 만 design.md 에 절을 새로 만들었다.
--   나머지는 해당 컬럼 주석에 그대로 남아 있다.
--
-- 곁들여 고친 것 셋 (길이가 아니라 내용 문제라 이 김에 바로잡는다) —
--   - store 와 vendor 가 둘 다 「납품처」였다. 방향이 정반대다
--     → store = 출고처(우리가 납품) / vendor = 매입처(벤더가 납품)
--   - ib_line 이 「입고 라인」이라 oms_ib_line 과 안 갈렸다 → 「입고예정(ASN) 라인」
--   - outb_order/outb_line 이 oms_outb_* 와 이름이 같았다 → (WMS) 표기
--
-- 무엇이 바뀌나: 테이블 주석 44개뿐이다. 컬럼 주석·제약·데이터·인덱스는 건드리지 않는다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — COMMENT 는 덮어쓰기이고, 없는 테이블은 건너뛴다
-- =====================================================================
DO $mig$
DECLARE
    r        record;
    v_done   int := 0;
    v_skip   int := 0;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            -- 마스터
            ('prod',              '상품 마스터'),
            ('prod_uom',          '상품 포장 (단위별 낱개수량·중량)'),
            ('zon',               '존 마스터 (로케이션의 상위 그룹)'),
            ('loc',               '로케이션 마스터 (재고가 놓이는 물리 위치)'),
            ('fxng_loc',          '고정 로케이션 마스터 (상품 전용 자리)'),
            ('lot',               'Lot 마스터 (입고 단위 묶음)'),
            ('store',             '점포 마스터 (출고처)'),
            ('vendor',            '벤더 마스터 (매입처)'),
            ('code_group',        '공통코드 그룹'),
            ('code_detail',       '공통코드 상세'),
            ('nbr_rule',          '채번 규칙'),
            ('nbr_seq',           '채번 카운터'),
            -- 주문 원장 (OMS)
            ('oms_ib_order',      '입고주문 헤더 (벤더 발주)'),
            ('oms_ib_line',       '입고주문 라인'),
            ('oms_outb_order',    '출고주문 헤더 (점포 수주)'),
            ('oms_outb_line',     '출고주문 라인'),
            -- 입고
            ('ib_order',          '입고예정(ASN) 헤더'),
            ('ib_line',           '입고예정(ASN) 라인'),
            ('putaway_task',      '적치 지시'),
            -- 재고
            ('inv',               '현재고 스냅샷 (키: 상품+Loc+Lot)'),
            ('inv_hist',          '재고 이력 (append-only 원장)'),
            ('inv_mov_task',      '이동지시 (보관↔보관)'),
            ('inv_hld',           '재고 보류 건'),
            ('inv_hld_acrst',     '보류 등록 실적 (append-only)'),
            ('inv_hld_rlz_acrst', '보류 해제 실적 (append-only)'),
            ('inv_stktk',         '재고조사(실사) 헤더'),
            ('inv_stktk_ln',      '재고조사 라인 (재고 키 단위)'),
            ('lot_attr_chng',     'Lot 속성 정정 이력 (수량 변동 없음)'),
            ('inv_lot_chng',      '재고 로트변경 이력 (재고를 다른 Lot으로 이동)'),
            -- 출고
            ('outb_wave',         '출고 웨이브 (피킹지시 발행 단위)'),
            ('outb_order',        '출고주문 헤더 (WMS)'),
            ('outb_line',         '출고주문 라인 (WMS)'),
            ('outb_alloc',        '재고 할당 레코드'),
            ('pikng_task',        '피킹 지시'),
            ('pikng_acrst',       '피킹 실적 (append-only)'),
            -- 전략
            ('stgy_rvsn',         '전략 리비전 스냅샷 (append-only)'),
            ('stgy_exec_log',     '전략 실행 로그'),
            ('insp_plcy',         '검수 정책 헤더'),
            ('insp_plcy_rule',    '검수 규칙'),
            ('ptawy_stgy',        '적치 전략 헤더'),
            ('ptawy_stgy_stg',    '적치 전략 단계'),
            ('wav_stgy',          '웨이브 전략'),
            ('aloc_stgy',         '할당 전략'),
            ('aloc_stgy_slot',    '할당 슬롯 (역할 5종)')
        ) AS t(tbl, cmt)
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_schema = current_schema() AND table_name = r.tbl) THEN
            EXECUTE format('COMMENT ON TABLE %I IS %L', r.tbl, r.cmt);
            v_done := v_done + 1;
        ELSE
            v_skip := v_skip + 1;
            RAISE NOTICE '% 없음 — 건너뜀', r.tbl;
        END IF;
    END LOOP;

    RAISE NOTICE '테이블 주석 간결화 완료 — 적용 % 건 / 건너뜀 % 건', v_done, v_skip;
END $mig$;

-- 확인 (44행, 전부 30자 이하여야 한다):
--   SELECT c.relname, obj_description(c.oid, 'pg_class') AS cmt,
--          length(obj_description(c.oid, 'pg_class')) AS len
--     FROM pg_class c
--     JOIN pg_namespace n ON n.oid = c.relnamespace
--    WHERE c.relkind = 'r' AND n.nspname = current_schema()
--    ORDER BY len DESC;
