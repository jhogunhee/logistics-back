-- =====================================================================
-- 개발 DB 업무 데이터 초기화 — 상품·포장과 그 아래 문서·재고만 지운다
--
--   지우는 것 : 입고주문 · ASN · 검수 · 적치 · 재고 · 이력 · 이동지시 · 보류 · 재고조사 · 출고 · Lot · 상품 · 포장
--   남기는 것 : 공통코드(UOM 그룹 포함) · 존 · 로케이션 · 벤더 · 점포
--
--   상품을 지우는 이유는 단위 때문이다. 상품의 출고단위는 재고 저장 단위라서, 단위를 바꾸면
--   그 상품에 이미 쌓인 재고 수량의 "의미"가 낱개에서 박스로 바뀐다. 수량을 SQL 로 환산해
--   맞추면 서비스 경로로 만든 inv_hist 와 어긋나 「이력 합계 = 스냅샷」 불변식이 깨지므로,
--   차라리 문서·재고를 비우고 검수부터 다시 하는 편이 깨끗하다.
--
--   마스터(공통코드 · 존 · 로케이션 · 벤더 · 점포)는 단위와 무관하므로 건드리지 않는다.
--   화면에서 손으로 추가한 존·로케이션도 그대로 살아남는다.
--
--   ▣ 되돌릴 수 없다. 개발 DB 전용이다.
--
--   실행 순서
--     1) 이 파일           — 업무 데이터 + 상품/포장 제거
--     2) docs/seed-dev.sql — 다시 시드 (마스터 · 채번 카운터는 ON CONFLICT 로 건너뛴다)
--   스키마가 이미 있으므로 schema.sql 은 돌리지 않는다.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     DELETE + 시퀀스 되감기라 몇 번을 돌려도 결과가 같다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $reset$
DECLARE
    t text;
    n bigint;
BEGIN
    -- 1. 업무 데이터 ----------------------------------------------------
    -- 위에서 아래로 = 하위 문서부터. FK 가 없어 순서가 강제되진 않지만 읽는 사람 기준의 순서다.
    -- putaway_task 는 스키마에만 있고 아직 엔티티가 없다 — 있으면 함께 비운다.
    FOREACH t IN ARRAY ARRAY[
        'outb_alloc', 'outb_line', 'outb_order', 'outb_wave',
        -- 재고를 참조하는 작업 문서(이동지시 · 보류 · 조사)를 재고보다 먼저 비운다.
        -- 재고만 지우면 이 문서들이 사라진 상품·Lot을 가리키는 유령 행으로 남는다.
        'inv_stktk_ln', 'inv_stktk',
        'inv_hld_rlz_acrst', 'inv_hld_acrst', 'inv_hld',
        'inv_mov_task',
        'inv_hist', 'inv', 'putaway_task',
        'ib_line', 'ib_order', 'oms_ib_line', 'oms_ib_order',
        -- Lot 속성 정정 이력은 lot 보다 먼저 — 재고 작업 문서와 같은 이유(유령 참조 방지).
        'lot_attr_chng',
        'lot', 'prod_uom', 'prod'
    ]
    LOOP
        IF to_regclass(t) IS NULL THEN
            RAISE NOTICE '% 테이블 없음 — 건너뜀', t;
            CONTINUE;
        END IF;
        EXECUTE format('DELETE FROM %I', t);
        GET DIAGNOSTICS n = ROW_COUNT;
        IF n > 0 THEN
            RAISE NOTICE '% : % 행 삭제', t, n;
        END IF;
    END LOOP;

    -- 2. 채번 카운터 되감기 ----------------------------------------------
    -- 채번은 전용 시퀀스가 아니라 nbr_seq 테이블이 센다 (규칙은 nbr_rule). 상품·문서를
    -- 비웠으므로 그 규칙들의 카운터 행을 지운다 — 다음 발급이 다시 1번부터 나온다.
    --
    -- VNDR_CD 는 지우지 않는다. 벤더를 남겼기 때문에 되감으면 VD-0001 이 다시 발급돼
    -- 화면에서 벤더를 등록할 때 uq_vndr_cd 위반이 난다.
    DELETE FROM nbr_seq
     WHERE rule_cd IN ('PROD_CD', 'OMS_IB_NO', 'IB_NO', 'OUTB_NO', 'OUTB_WAV_NO',
                       'INV_MOV_NO', 'HLD_NO', 'STKTK_NO');

    GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE '채번 카운터 % 행 삭제 (VNDR_CD 는 벤더를 남겨 두므로 유지)', n;

    -- 3. 남은 마스터 확인 -------------------------------------------------
    SELECT count(*) INTO n FROM code_detail WHERE grp_cd = 'UOM';
    RAISE NOTICE '유지 — 공통코드 UOM 그룹 % 건', n;
    SELECT count(*) INTO n FROM loc;
    RAISE NOTICE '유지 — 로케이션 % 건', n;
    SELECT count(*) INTO n FROM vendor;
    RAISE NOTICE '유지 — 벤더 % 건', n;
    SELECT count(*) INTO n FROM store;
    RAISE NOTICE '유지 — 점포 % 건', n;

    RAISE NOTICE '초기화 완료 — 이제 docs/seed-dev.sql 을 실행할 것';
END
$reset$;

-- =====================================================================
-- 적용 후 확인 (seed-dev.sql 까지 돌린 뒤)
--   SELECT 'prod' t, count(*) FROM prod
--   UNION ALL SELECT 'prod_uom', count(*) FROM prod_uom
--   UNION ALL SELECT 'oms_ib_order', count(*) FROM oms_ib_order
--   UNION ALL SELECT 'oms_ib_line', count(*) FROM oms_ib_line
--   UNION ALL SELECT 'ib_order', count(*) FROM ib_order
--   UNION ALL SELECT 'ib_line', count(*) FROM ib_line
--   UNION ALL SELECT 'inv', count(*) FROM inv
--   UNION ALL SELECT 'inv_hist', count(*) FROM inv_hist;
--
--   기대값: prod 21 · prod_uom 46 · oms_ib_order 5 · oms_ib_line 15 ·
--           ib_order 4 · ib_line 13 · inv 0 · inv_hist 0
--   (oms_ib_line 15 = 3+3+4+3+2. 미변환 주문 VD-0005 의 2건을 뺀 13건이 ib_line.
--    재고는 0 — 검수는 화면에서 진행해야 이력 합계 = 스냅샷 불변식이 지켜진다)
-- =====================================================================
