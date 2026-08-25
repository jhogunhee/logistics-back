-- =====================================================================
-- 반품입고 — 입고주문·입고예정의 상대처 일반화(벤더 또는 점포) + 반품사유 + 불량수량 + 반품존.
--
-- 현재 라이브 상태 → schema.sql 상태.
-- 근거: docs/design.md 「반품입고」. 새 문서를 만들지 않고 입고주문의 구분(odr_dvsn=RTNGS)으로 받는다.
--   - oms_ib_order · ib_order: vendor_id NULL 허용 + store_id, 둘 중 하나 CHECK.
--   - oms_ib_order.ref_outb_no: 원 출고번호(느슨한 참조, 선택).
--   - oms_ib_line.rsn_cd · rsn_dscr: 반품사유 (공통코드 RTNGS_RSN).
--   - ib_line.rjct_qty: 검수 불량 누계. rcvd_qty는 양품만이라 ptawy<=rcvd 제약은 그대로.
--   - 반품존 3 + 로케이션 3 (STORAGE — 보류가 보관 로케이션만 받는다).
--
-- 실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--   - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--   - 재실행 안전 — 존재 확인을 걸어 이미 적용된 항목은 건너뛴다
-- =====================================================================
DO $mig$
BEGIN
    -- 1. oms_ib_order ---------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'oms_ib_order' AND column_name = 'store_id') THEN
        ALTER TABLE oms_ib_order ALTER COLUMN vendor_id DROP NOT NULL;
        ALTER TABLE oms_ib_order ADD COLUMN store_id BIGINT;
        ALTER TABLE oms_ib_order ADD COLUMN ref_outb_no VARCHAR(30);
        ALTER TABLE oms_ib_order ADD CONSTRAINT ck_oms_ib_order_vndr_store
            CHECK ((vendor_id IS NOT NULL) <> (store_id IS NOT NULL));
        COMMENT ON COLUMN oms_ib_order.vendor_id   IS '납품 벤더. 반품입고(odr_dvsn=RTNGS)는 NULL';
        COMMENT ON COLUMN oms_ib_order.store_id    IS '반품 점포. 반품입고만, 벤더와 둘 중 하나';
        COMMENT ON COLUMN oms_ib_order.ref_outb_no IS '원 출고번호. 반품입고만, 선택 (느슨한 참조)';
        RAISE NOTICE 'oms_ib_order.store_id · ref_outb_no 추가, vendor_id NULL 허용';
    ELSE
        RAISE NOTICE 'oms_ib_order.store_id 이미 존재 — 건너뜀';
    END IF;

    -- 2. oms_ib_line ----------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'oms_ib_line' AND column_name = 'rsn_cd') THEN
        ALTER TABLE oms_ib_line ADD COLUMN rsn_cd VARCHAR(10);
        ALTER TABLE oms_ib_line ADD COLUMN rsn_dscr VARCHAR(200);
        COMMENT ON COLUMN oms_ib_line.odr_qty  IS '발주 수량 (정상 입고단위 · 반품 출고단위)';
        COMMENT ON COLUMN oms_ib_line.rsn_cd   IS '반품사유. RTNGS_RSN. 반품 라인만';
        COMMENT ON COLUMN oms_ib_line.rsn_dscr IS '반품사유 상세. ETC일 때만';
        RAISE NOTICE 'oms_ib_line.rsn_cd · rsn_dscr 추가';
    ELSE
        RAISE NOTICE 'oms_ib_line.rsn_cd 이미 존재 — 건너뜀';
    END IF;

    -- 3. ib_order -------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'ib_order' AND column_name = 'store_id') THEN
        ALTER TABLE ib_order ALTER COLUMN vendor_id DROP NOT NULL;
        ALTER TABLE ib_order ADD COLUMN store_id BIGINT;
        ALTER TABLE ib_order ADD CONSTRAINT ck_ib_order_vndr_store
            CHECK ((vendor_id IS NOT NULL) <> (store_id IS NOT NULL));
        COMMENT ON COLUMN ib_order.vendor_id IS '납품 벤더. 반품입고는 NULL';
        COMMENT ON COLUMN ib_order.store_id  IS '반품 점포. 반품입고만';
        RAISE NOTICE 'ib_order.store_id 추가, vendor_id NULL 허용';
    ELSE
        RAISE NOTICE 'ib_order.store_id 이미 존재 — 건너뜀';
    END IF;

    -- 4. ib_line.rjct_qty -----------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'ib_line' AND column_name = 'rjct_qty') THEN
        ALTER TABLE ib_line ADD COLUMN rjct_qty BIGINT DEFAULT 0 NOT NULL;
        ALTER TABLE ib_line DROP CONSTRAINT IF EXISTS ck_ib_line_qty;
        ALTER TABLE ib_line ADD CONSTRAINT ck_ib_line_qty CHECK (
            expct_qty > 0 AND rcvd_qty >= 0 AND rjct_qty >= 0
            AND ptawy_qty >= 0 AND ptawy_qty <= rcvd_qty
        );
        COMMENT ON COLUMN ib_line.rcvd_qty IS '검수 양품 수량 누계 (스테이징 입)';
        COMMENT ON COLUMN ib_line.rjct_qty IS '검수 불량 수량 누계 (반품존 입). 반품입고만';
        RAISE NOTICE 'ib_line.rjct_qty 추가, ck_ib_line_qty 재정의';
    ELSE
        RAISE NOTICE 'ib_line.rjct_qty 이미 존재 — 건너뜀';
    END IF;

    -- 5. 공통코드 RTNGS_RSN ----------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM code_group WHERE grp_cd = 'RTNGS_RSN') THEN
        INSERT INTO code_group (grp_cd, grp_nm, dscr) VALUES
            ('RTNGS_RSN', '반품사유', '반품입고 라인의 반품 사유 (oms_ib_line.rsn_cd). ETC(기타)일 때만 자유 텍스트 rsn_dscr를 받는다');
        INSERT INTO code_detail (grp_cd, code_cd, code_nm, srt_seq) VALUES
            ('RTNGS_RSN', 'MISDLV', '오배송', 1),
            ('RTNGS_RSN', 'DAMG', '파손', 2),
            ('RTNGS_RSN', 'EXPIRY', '유통기한임박', 3),
            ('RTNGS_RSN', 'UNSOLD', '미판매', 4),
            ('RTNGS_RSN', 'ETC', '기타', 5);
        RAISE NOTICE '공통코드 RTNGS_RSN 그룹 + 코드 5건 시드';
    ELSE
        RAISE NOTICE '공통코드 RTNGS_RSN 이미 존재 — 건너뜀';
    END IF;

    -- 6. 반품존 + 로케이션 ------------------------------------------------------
    INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-DRY', '상온 반품존', 'DRY', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
    INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-CHL', '냉장 반품존', 'CHL', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
    INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-FRZ', '냉동 반품존', 'FRZ', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
    INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
    SELECT 'RTN-DRY-01', zon_id, 'DRY', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-DRY' ON CONFLICT (loc_cd) DO NOTHING;
    INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
    SELECT 'RTN-CHL-01', zon_id, 'CHL', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-CHL' ON CONFLICT (loc_cd) DO NOTHING;
    INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
    SELECT 'RTN-FRZ-01', zon_id, 'FRZ', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-FRZ' ON CONFLICT (loc_cd) DO NOTHING;
    RAISE NOTICE '반품존 3 + 로케이션 3 (이미 있으면 유지)';

    -- 7. store_id 인덱스 --------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ix_ib_order_store') THEN
        CREATE INDEX ix_ib_order_store ON ib_order (store_id);
        RAISE NOTICE 'ix_ib_order_store 생성';
    ELSE
        RAISE NOTICE 'ix_ib_order_store 이미 존재 — 건너뜀';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ix_oms_ib_order_store') THEN
        CREATE INDEX ix_oms_ib_order_store ON oms_ib_order (store_id);
        RAISE NOTICE 'ix_oms_ib_order_store 생성';
    ELSE
        RAISE NOTICE 'ix_oms_ib_order_store 이미 존재 — 건너뜀';
    END IF;
END
$mig$;

-- 확인:
--   SELECT column_name FROM information_schema.columns WHERE table_name IN ('oms_ib_order','ib_order') AND column_name = 'store_id';
--   SELECT z.zon_cd, l.loc_cd, l.loc_typ FROM loc l JOIN zon z ON z.zon_id = l.zon_id WHERE z.biz_dvsn = 'RTNGS';
