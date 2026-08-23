-- =====================================================================
-- 컬럼 주석 간결화 — 224개 전부 「이 컬럼이 무엇인가」 한 줄로.
--
-- 배경: 컬럼 주석이 총 16,057자(평균 72자, 최대 523자)까지 자라 DBeaver에서
--   컬럼 목록을 훑을 때 오히려 안 읽혔다. 테이블 주석에 이어 같은 정리를 한다.
--   → 224개를 이름 + (필요한 경우 한 마디)로 줄인다. 총 2,834자, 평균 13자, 최대 48자.
--
--   이름 뒤에 한 마디를 붙인 것은 이름만으로 못 읽는 넷뿐이다 —
--     단위(oms_outb_line.odr_qty 는 출고단위, outb_line.odr_qty 는 낱개 — 이름이 같다)
--     enum 이름(값 나열 대신 OutbStatus enum 처럼 코드를 가리킨다)
--     NULL 의미(shelf_life_days 의 NULL 은 「값 없음」이 아니라 「유통기한 미관리」다)
--     JSON 형식(cond_grp 는 모양을 모르면 읽을 수 없다)
--
--   잘려나간 근거는 잃지 않았다 — 항등식 · 번복 이력 · 다른 모듈의 동작은
--   docs/design.md 와 CLAUDE.md 에 이미 있는 것을 확인하고 잘랐다.
--
-- 무엇이 바뀌나: 컬럼 주석 224개뿐이다. 테이블 주석·제약·데이터·인덱스는 건드리지 않는다.
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — COMMENT 는 덮어쓰기이고, 없는 컬럼은 건너뛴다
-- =====================================================================
DO $mig$
DECLARE
    r      record;
    v_done int := 0;
    v_skip int := 0;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            ('prod', 'prod_cd', '상품 코드'),
            ('prod', 'tmp_zon', '보관 온도대. TmpZon enum'),
            ('prod', 'inb_uom_cd', '입고단위'),
            ('prod', 'outb_uom_cd', '출고단위'),
            ('prod', 'shelf_life_days', '제조일 기준 총 유통기한. NULL = 유통기한 미관리'),
            ('prod', 'img_url', '상품 이미지. NULL = 이미지 없음'),
            ('prod_uom', 'uom_cd', '단위 코드'),
            ('prod_uom', 'ea_qty', '이 단위 1개가 낱개 몇 개인가'),
            ('prod_uom', 'wgt', '이 단위 1개의 중량(kg). NULL = 미측정'),
            ('zon', 'zon_cd', '존 코드'),
            ('zon', 'zon_nm', '존 명'),
            ('zon', 'tmp_zon', '온도대. TmpZon enum'),
            ('zon', 'strg_typ', '보관유형. StrgTyp enum'),
            ('zon', 'biz_dvsn', '업무구분. BizDvsn enum'),
            ('loc', 'loc_cd', '로케이션 코드'),
            ('loc', 'zon_id', '소속 존'),
            ('loc', 'tmp_zon', '온도대. TmpZon enum'),
            ('loc', 'loc_typ', '로케이션 유형. LocTyp enum'),
            ('loc', 'pikng_prty', '할당 시 동일 유통기한'),
            ('loc', 'ptawy_prty', '적치 우선순위'),
            ('loc', 'max_qty', '최대 적재 수량. NULL = 무제한'),
            ('fxng_loc', 'prod_id', '고정할 상품'),
            ('fxng_loc', 'loc_id', '고정 로케이션'),
            ('fxng_loc', 'min_qty', '재보충점'),
            ('fxng_loc', 'max_qty', '보충 목표 상한'),
            ('lot', 'lot_no', 'Lot 번호'),
            ('lot', 'receipt_dt', '입고일자 (소급 등록 가능)'),
            ('lot', 'mfg_dt', '제조일자. NULL = 유통기한 미관리 상품'),
            ('lot', 'expiry_dt', '유통기한. NULL = 미관리 상품의 Lot'),
            ('store', 'store_cd', '점포 코드'),
            ('store', 'store_grp', '점포그룹. NULL = 미지정'),
            ('store', 'store_typ', '점포유형. NULL = 미지정'),
            ('store', 'outb_life_rate', '납품 허용 잔여수명 비율'),
            ('vendor', 'vndr_cd', '벤더 코드'),
            ('code_group', 'grp_cd', '코드 그룹 코드'),
            ('code_group', 'dscr', '그룹 설명'),
            ('code_detail', 'code_cd', '코드 값'),
            ('code_detail', 'srt_seq', '화면 표시 정렬 순서'),
            ('nbr_rule', 'rule_cd', '채번 규칙 코드'),
            ('nbr_rule', 'prfx', '접두어 리터럴'),
            ('nbr_rule', 'prfx_dlmt', '접두어 뒤 구분자'),
            ('nbr_rule', 'de_dlmt', '날짜 뒤 구분자'),
            ('nbr_rule', 'seq_dgt', 'SEQ 자릿수'),
            ('nbr_rule', 'dync_ky_typ', '동적키 유형. DyncKyTyp enum'),
            ('nbr_seq', 'dync_ky', '동적키 값'),
            ('nbr_seq', 'seq', '현재 발급값'),
            ('oms_ib_order', 'oms_ib_no', '입고주문 번호'),
            ('oms_ib_order', 'status', '입고주문 상태. OmsIbStatus enum'),
            ('oms_ib_order', 'vendor_id', '납품 벤더'),
            ('oms_ib_order', 'expct_de', '입고 예정일'),
            ('oms_ib_order', 'odr_dvsn', '발주구분'),
            ('oms_ib_order', 'pic_nm', '발주 담당자명'),
            ('oms_ib_order', 'rmk', '비고'),
            ('oms_ib_order', 'cfm_dt', '확정(ASN 생성) 시각. NULL = 미확정'),
            ('oms_ib_line', 'odr_qty', '발주 수량 (입고단위)'),
            ('oms_outb_order', 'oms_outb_no', '출고주문 번호'),
            ('oms_outb_order', 'status', '출고주문 상태. OmsOutbStatus enum'),
            ('oms_outb_order', 'store_id', '납품처 점포'),
            ('oms_outb_order', 'outb_typ', '출고유형'),
            ('oms_outb_order', 'vhcl_fltno', '차량편수. NULL = 배차 미정'),
            ('oms_outb_order', 'expct_de', '출고 예정일'),
            ('oms_outb_order', 'pic_nm', '수주 담당자명'),
            ('oms_outb_order', 'rmk', '비고'),
            ('oms_outb_order', 'cfm_dt', '확정 시각. NULL = 미확정'),
            ('oms_outb_line', 'odr_qty', '주문 수량 (출고단위)'),
            ('ib_order', 'ib_no', '입고 번호'),
            ('ib_order', 'oms_ib_order_id', '이 ASN을 발생시킨 OMS 입고주문'),
            ('ib_order', 'status', '입고예정 상태. IbStatus enum'),
            ('ib_order', 'vendor_id', '납품 벤더'),
            ('ib_order', 'expct_de', '입고 예정일'),
            ('ib_order', 'odr_dvsn', '발주구분'),
            ('ib_order', 'cfm_dt', '입고확정 시각'),
            ('ib_line', 'expct_qty', '입고 예정 수량'),
            ('ib_line', 'rcvd_qty', '검수'),
            ('ib_line', 'ptawy_qty', '적치 완료 수량 누계'),
            ('putaway_task', 'ib_line_id', '적치 대상 입고 라인'),
            ('putaway_task', 'lot_id', '적치 대상 Lot'),
            ('putaway_task', 'to_loc_id', '지시된 보관 로케이션'),
            ('putaway_task', 'drct_qty', '지시 수량'),
            ('putaway_task', 'cmpl_qty', '실행'),
            ('putaway_task', 'status', '적치지시 상태. PutawayTaskStatus enum'),
            ('putaway_task', 'cmpl_dt', '지시 완료 시각'),
            ('inv', 'on_hand_qty', '실물 보유 수량'),
            ('inv', 'aloc_qty', '예약 수량'),
            ('inv', 'hld_qty', '보류 수량'),
            ('inv', 'version', '낙관적 락 버전'),
            ('inv_hist', 'tx_typ', '거래유형. TxTyp enum'),
            ('inv_hist', 'qty', '변동 수량'),
            ('inv_hist', 'rfn_doc_typ', '참조 문서 유형. RefDocTyp enum. NULL = 수동조정'),
            ('inv_hist', 'rfn_doc_no', '참조 문서 번호'),
            ('inv_hist', 'ib_line_id', '입고 라인 ID'),
            ('inv_hist', 'from_loc_id', 'MOVE의 출발지. MOVE가 아니면 NULL'),
            ('inv_hist', 'to_loc_id', 'MOVE의 도착지. MOVE가 아니면 NULL'),
            ('inv_hist', 'cncl_inv_hist_id', '검수 취소가 되돌리는 원본 RECEIVE 건. ADJUST가 아니면 NULL'),
            ('inv_mov_task', 'inv_mov_no', '이동지시 번호'),
            ('inv_mov_task', 'mov_dvsn', '이동구분. InvMovDvsn enum'),
            ('inv_mov_task', 'prod_id', '이동 대상 상품'),
            ('inv_mov_task', 'from_loc_id', '출발 보관 로케이션'),
            ('inv_mov_task', 'to_loc_id', '도착 보관 로케이션'),
            ('inv_mov_task', 'drct_qty', '지시 수량'),
            ('inv_mov_task', 'cmpl_qty', '확정'),
            ('inv_mov_task', 'status', '이동지시 상태. InvMovStatus enum'),
            ('inv_mov_task', 'cmpl_dt', '지시 완료 시각'),
            ('inv_mov_task', 'pikng_task_id', '짝 피킹지시'),
            ('inv_hld', 'hld_no', '보류 번호'),
            ('inv_hld', 'prod_id', '보류 대상 상품'),
            ('inv_hld', 'hld_qty', '보류 수량'),
            ('inv_hld', 'rlz_qty', '해제 완료 수량 누계'),
            ('inv_hld', 'rsn_cd', '보류 사유 코드'),
            ('inv_hld', 'rsn_dscr', '기타 사유 텍스트'),
            ('inv_hld', 'status', '보류 상태. InvHldStatus enum'),
            ('inv_hld', 'rlz_dt', '전량 해제 시각'),
            ('inv_stktk', 'stktk_no', '재고조사 번호'),
            ('inv_stktk', 'zon_cd', '조사 범위 — 존 코드. NULL = 조건 없음'),
            ('inv_stktk', 'loc_id', '조사 범위 — 로케이션. NULL = 조건 없음'),
            ('inv_stktk', 'prod_id', '조사 범위 — 상품. NULL = 조건 없음'),
            ('inv_stktk', 'status', '재고조사 상태. InvStktkStatus enum'),
            ('inv_stktk', 'cfm_dt', '확정 시각'),
            ('inv_stktk_ln', 'sys_qty', '조사 생성 시점의 전산수량 스냅샷'),
            ('inv_stktk_ln', 'stktk_qty', '실사수량. NULL = 미조사'),
            ('inv_stktk_ln', 'cfm_sys_qty', '확정 시점 전산수량(= 조정전수량). NULL = 확정 전'),
            ('inv_stktk_ln', 'rsn_cd', '조정사유 코드'),
            ('inv_stktk_ln', 'rsn_dscr', '기타 사유 텍스트'),
            ('lot_attr_chng', 'lot_id', '정정 대상 Lot'),
            ('lot_attr_chng', 'prod_id', '대상 Lot의 상품'),
            ('lot_attr_chng', 'lot_no', 'Lot 번호 스냅샷'),
            ('lot_attr_chng', 'bfr_mfg_dt', '제조일자 변경 전 값'),
            ('lot_attr_chng', 'aft_mfg_dt', '제조일자 변경 후 값'),
            ('lot_attr_chng', 'bfr_expiry_dt', '유통기한 변경 전 값'),
            ('lot_attr_chng', 'aft_expiry_dt', '유통기한 변경 후 값'),
            ('lot_attr_chng', 'rsn_cd', '정정 사유 코드'),
            ('lot_attr_chng', 'rsn_dscr', '기타 사유 텍스트'),
            ('inv_lot_chng', 'lot_chng_no', '로트변경 번호'),
            ('inv_lot_chng', 'prod_id', '대상 상품'),
            ('inv_lot_chng', 'loc_id', '대상 보관 로케이션'),
            ('inv_lot_chng', 'from_lot_id', '원 Lot'),
            ('inv_lot_chng', 'from_lot_no', '원 Lot 번호 스냅샷'),
            ('inv_lot_chng', 'from_mfg_dt', '원 Lot 제조일자 스냅샷'),
            ('inv_lot_chng', 'from_expiry_dt', '원 Lot 유통기한 스냅샷'),
            ('inv_lot_chng', 'to_lot_id', '목적지 Lot'),
            ('inv_lot_chng', 'to_lot_no', '목적지 Lot 번호 스냅샷'),
            ('inv_lot_chng', 'to_mfg_dt', '정정된 제조일자'),
            ('inv_lot_chng', 'to_expiry_dt', '정정된 유통기한'),
            ('inv_lot_chng', 'chng_qty', '변경 수량'),
            ('inv_lot_chng', 'to_lot_new_yn', '목적지 Lot을 이번에 채번했는가'),
            ('inv_lot_chng', 'rsn_cd', '변경 사유 코드'),
            ('inv_lot_chng', 'rsn_dscr', '기타 사유 텍스트'),
            ('outb_wave', 'wav_no', '웨이브 번호'),
            ('outb_wave', 'status', '웨이브 상태. WaveStatus enum'),
            ('outb_wave', 'issued_dt', '피킹지시 발행 시각. NULL = 미발행'),
            ('outb_wave', 'clos_dt', '종료 시각'),
            ('outb_wave', 'wav_stgy_id', '이 웨이브를 만든 웨이브 전략. NULL = 화면에서 수동 생성'),
            ('outb_wave', 'rvsn_no', '생성에 사용된 전략 리비전'),
            ('outb_order', 'outb_no', '출고 번호'),
            ('outb_order', 'oms_outb_order_id', '이 출고주문을 발생시킨 OMS 출고주문'),
            ('outb_order', 'status', '출고 상태. OutbStatus enum'),
            ('outb_order', 'outb_typ', '출고유형'),
            ('outb_order', 'vhcl_fltno', '차량편수. NULL = 배차 미정'),
            ('outb_order', 'store_id', '출고처 점포'),
            ('outb_order', 'wav_id', '편성된 출고 웨이브. NULL = 아직 미편성'),
            ('outb_order', 'wav_reg_typ', '웨이브 편입 출처. WavRegTyp enum. NULL = 미편성'),
            ('outb_order', 'odr_de', '주문일 = 상위 OMS 출고주문이 등록된 날'),
            ('outb_order', 'shmt_dt', '출고 확정 시각'),
            ('outb_order', 'expct_de', '출고 예정일'),
            ('outb_line', 'odr_qty', '주문 수량'),
            ('outb_alloc', 'aloc_qty', '할당 수량'),
            ('outb_alloc', 'aloc_stgy_id', '이 할당을 만든 할당 전략. NULL = 수동할당 또는 전략 미설정 기간의 기본 동작'),
            ('outb_alloc', 'rvsn_no', '할당에 사용된 전략 리비전'),
            ('outb_alloc', 'pikng_qty', '피킹 완료 수량'),
            ('pikng_task', 'outb_wave_id', '발행 웨이브'),
            ('pikng_task', 'outb_alloc_id', '지시의 근거 할당'),
            ('pikng_task', 'prod_id', '재고 키 스냅샷'),
            ('pikng_task', 'from_loc_id', '집품 로케이션 = 할당된 재고의 로케이션 스냅샷'),
            ('pikng_task', 'lot_id', '집품 Lot 스냅샷'),
            ('pikng_task', 'drct_qty', '지시 수량 = 발행 시점의 aloc_qty'),
            ('pikng_task', 'cmpl_qty', '실행'),
            ('pikng_task', 'status', '피킹지시 상태. PikngTaskStatus enum'),
            ('pikng_task', 'srt_seq', '집품 순서'),
            ('pikng_task', 'cmpl_dt', '지시 완료 시각'),
            ('pikng_task', 'shotge_qty', '결품 수량'),
            ('pikng_task', 'shotge_rsn_cd', '결품 사유 코드. NULL = 결품 아님'),
            ('pikng_task', 'shotge_rsn_dscr', '기타 결품사유 텍스트'),
            ('pikng_acrst', 'pikng_task_id', '실행한 피킹지시'),
            ('pikng_acrst', 'pikng_qty', '이번 실행의 피킹 수량'),
            ('stgy_rvsn', 'stgy_typ', '전략 유형. StgyTyp enum'),
            ('stgy_rvsn', 'stgy_id', '유형별 헤더 PK'),
            ('stgy_rvsn', 'rvsn_no', '전략별 1부터 증가'),
            ('stgy_rvsn', 'snpsht', '정의 전체'),
            ('insp_plcy', 'stgy_nm', '정책명'),
            ('insp_plcy', 'last_rvsn_no', '마지막 저장 리비전'),
            ('insp_plcy_rule', 'insp_plcy_id', '소속 정책'),
            ('insp_plcy_rule', 'srt_seq', '평가·표시 순서'),
            ('insp_plcy_rule', 'rule_cd', '코드 레지스트리의 규칙 code'),
            ('insp_plcy_rule', 'para', '규칙 파라미터'),
            ('ptawy_stgy', 'stgy_nm', '전략명'),
            ('ptawy_stgy', 'odr_dvsn', '적용대상 발주구분. NULL = 전체'),
            ('ptawy_stgy', 'unt_splt_yn', '입수 단위 배수 절사'),
            ('ptawy_stgy', 'loc_srt', '후보 정렬 [{"field":…,"dir":ASC|DESC}]. 빈 배열 = 기본'),
            ('ptawy_stgy', 'last_rvsn_no', '마지막 저장 리비전'),
            ('ptawy_stgy_stg', 'ptawy_stgy_id', '소속 전략'),
            ('ptawy_stgy_stg', 'srt_seq', '실행 순서'),
            ('ptawy_stgy_stg', 'mthd_cd', '추천 방식 code'),
            ('ptawy_stgy_stg', 'mthd_para', '방식 파라미터'),
            ('ptawy_stgy_stg', 'line_cond', '조건 [{fld,op,vals}]'),
            ('ptawy_stgy_stg', 'loc_cond', '적치위치 지정 [{"fld","op","vals"}]. 빈 배열 = 전체 보관 로케이션'),
            ('wav_stgy', 'stgy_nm', '전략명'),
            ('wav_stgy', 'prty', '실행 순서'),
            ('wav_stgy', 'cond_grp', '조건그룹 [[{fld,op,vals},…],…]. 그룹끼리 OR, 그룹 안 AND'),
            ('wav_stgy', 'last_rvsn_no', '마지막 저장 리비전'),
            ('aloc_stgy', 'stgy_nm', '전략명'),
            ('aloc_stgy', 'prty', '선택 순서'),
            ('aloc_stgy', 'tgt_cond', '적용대상 조건 [{fld,op,vals},…]. 빈 배열 = 전체'),
            ('aloc_stgy', 'last_rvsn_no', '마지막 저장 리비전'),
            ('aloc_stgy_slot', 'aloc_stgy_id', '할당 전략 헤더'),
            ('aloc_stgy_slot', 'slot_typ', '슬롯 유형. AlocSlotTyp enum'),
            ('aloc_stgy_slot', 'srt_seq', '다중 슬롯 안의 순서'),
            ('aloc_stgy_slot', 'cmpnt_cd', '구현체 code (enum name). 구현체 축이 없는 슬롯은 NULL'),
            ('aloc_stgy_slot', 'para', '슬롯 파라미터 [{"field","dir"},…]'),
            ('aloc_stgy_slot', 'cond', '슬롯 조건 [{fld,op,vals},…]')
        ) AS t(tbl, col, cmt)
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = r.tbl AND column_name = r.col) THEN
            EXECUTE format('COMMENT ON COLUMN %I.%I IS %L', r.tbl, r.col, r.cmt);
            v_done := v_done + 1;
        ELSE
            RAISE NOTICE '건너뜀 — 없는 컬럼: %.%', r.tbl, r.col;
            v_skip := v_skip + 1;
        END IF;
    END LOOP;

    RAISE NOTICE '컬럼 주석 간결화: %건 적용, %건 건너뜀', v_done, v_skip;
END
$mig$;

-- 확인 (전부 48자 이하여야 한다):
--   SELECT c.relname, a.attname, length(col_description(c.oid, a.attnum)) AS len
--     FROM pg_class c
--     JOIN pg_namespace n ON n.oid = c.relnamespace
--     JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0
--    WHERE c.relkind = 'r' AND n.nspname = current_schema()
--      AND col_description(c.oid, a.attnum) IS NOT NULL
--    ORDER BY len DESC LIMIT 15;
