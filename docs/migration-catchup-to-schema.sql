-- =====================================================================
-- 따라잡기(catch-up) — 현재 DB를 docs/schema.sql 최종 상태로 올린다
--   대상: sku→prod 개명은 끝났지만 vendor · oms_ib_* · putaway_task 가 없고
--         1단계 컬럼 개명(tmp_zon · aloc_qty · odr_qty …)도 안 된 DB.
--
--   왜 새로 만들었나 —
--   migration-inbound-putaway.sql 과 migration-oms-inbound.sql 은 `sku` 를 참조한다.
--   그런데 이 DB는 이미 prod 로 개명된 뒤라 그 둘은 영영 실행될 수 없다.
--   "과거 증분을 순서대로 재생한다"는 전제가 이 DB에서는 이미 깨져 있어서,
--   그 둘을 대체하는 스크립트를 현재 이름 기준으로 다시 썼다.
--   => migration-inbound-putaway.sql · migration-oms-inbound.sql 은 이제 돌리지 말 것.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다).
--     DO 는 "문장 하나"라서 auto-commit 이 그대로 먹고, 내부에서 실패하면 통째로
--     롤백되며 세션에 죽은 트랜잭션이 남지 않는다.
--     BEGIN; 으로 감싸면 실패 시 연결이 aborted 상태로 남아 이후 모든 쿼리가
--     25P02 를 뱉는데(DBeaver 에서 겪은 그 증상), 그걸 피하려는 구조다.
--
--   전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--   이미 있는 것은 건너뛰고 NOTICE 로 알린다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $catchup$
DECLARE
    r        record;
    n        int := 0;
    v_oms_id bigint;
BEGIN
    -- -----------------------------------------------------------------
    -- (0) 전제 확인
    -- -----------------------------------------------------------------
    IF to_regclass('prod') IS NULL THEN
        IF to_regclass('sku') IS NOT NULL THEN
            RAISE EXCEPTION 'prod 가 없고 sku 가 있다 — docs/migration-sku-to-prod.sql 을 먼저 돌릴 것';
        END IF;
        RAISE EXCEPTION 'prod 도 sku 도 없다 — 스키마(%)가 비었다. docs/schema.sql 을 통째로 적용할 것', current_schema();
    END IF;
    RAISE NOTICE '전제 확인 완료 — 스키마 %', current_schema();

    -- -----------------------------------------------------------------
    -- (1) 남은 FK 전량 제거
    --     PK · UNIQUE · CHECK 는 건드리지 않는다 (contype='f' 만).
    -- -----------------------------------------------------------------
    FOR r IN SELECT c.conname, t.relname AS tbl
               FROM pg_constraint c
               JOIN pg_class t ON t.oid = c.conrelid
               JOIN pg_namespace s ON s.oid = t.relnamespace
              WHERE c.contype = 'f' AND s.nspname = current_schema()
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.tbl, r.conname);
        n := n + 1;
    END LOOP;
    RAISE NOTICE 'FK % 건 제거', n;

    -- -----------------------------------------------------------------
    -- (2) 1단계 컬럼 개명 — 기존 테이블만.
    --     아래에서 새로 만드는 테이블은 처음부터 최종 이름으로 만든다.
    -- -----------------------------------------------------------------
    n := 0;
    FOR r IN
        SELECT * FROM (VALUES
            ('prod',       'temp_zone',           'tmp_zon'),
            ('loc',        'temp_zone',           'tmp_zon'),
            ('loc',        'zone_cd',             'zon_cd'),
            ('loc',        'loc_type',            'loc_typ'),
            ('loc',        'pick_prty',           'pikng_prty'),
            ('ib_line',    'ptwy_qty',            'ptawy_qty'),
            ('ib_order',   'expct_dt',            'expct_de'),
            ('ib_order',   'closed_at',           'clos_dt'),
            ('inv',        'alloc_qty',           'aloc_qty'),
            ('inv_hist',   'tx_type',             'tx_typ'),
            ('inv_hist',   'ref_doc_type',        'rfn_doc_typ'),
            ('inv_hist',   'ref_doc_no',          'rfn_doc_no'),
            ('inv_hist',   'cancels_inv_hist_id', 'cncl_inv_hist_id'),
            ('outb_wave',  'wave_no',             'wav_no'),
            ('outb_order', 'wave_id',             'wav_id'),
            ('outb_order', 'order_dt',            'odr_de'),
            ('outb_order', 'shipped_at',          'shmt_dt'),
            ('outb_line',  'order_qty',           'odr_qty'),
            ('outb_alloc', 'alloc_qty',           'aloc_qty'),
            ('outb_alloc', 'picked_qty',          'pikng_qty'),
            ('code_group', 'group_cd',            'grp_cd'),
            ('code_group', 'group_nm',            'grp_nm'),
            ('code_group', 'description',         'dscr'),
            ('code_detail','group_cd',            'grp_cd'),
            ('code_detail','use_yn',              'us_yn'),
            ('code_detail','sort_ord',            'srt_seq')
        ) AS v(tbl, old_nm, new_nm)
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = r.tbl AND column_name = r.old_nm) THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN %I TO %I', r.tbl, r.old_nm, r.new_nm);
            n := n + 1;
        END IF;
    END LOOP;
    RAISE NOTICE '컬럼 % 개 개명', n;

    -- 개명된 컬럼을 이름에 담고 있던 제약·인덱스도 맞춘다
    FOR r IN
        SELECT * FROM (VALUES
            ('prod',       'ck_prod_temp_zone', 'ck_prod_tmp_zon'),
            ('loc',        'ck_loc_temp_zone',  'ck_loc_tmp_zon'),
            ('loc',        'ck_loc_type',       'ck_loc_typ'),
            ('inv_hist',   'ck_invh_tx_type',   'ck_invh_tx_typ'),
            ('code_detail','ck_code_use_yn',    'ck_code_us_yn'),
            ('outb_wave',  'uq_wave_no',        'uq_wav_no'),
            ('outb_alloc', 'ck_alloc_qty',      'ck_aloc_qty')
        ) AS v(tbl, old_nm, new_nm)
    LOOP
        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = r.old_nm) THEN
            EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I', r.tbl, r.old_nm, r.new_nm);
        END IF;
    END LOOP;

    FOR r IN
        SELECT * FROM (VALUES
            ('ix_invh_ref_doc',    'ix_invh_rfn_doc'),
            ('ix_outb_order_wave', 'ix_outb_order_wav')
        ) AS v(old_nm, new_nm)
    LOOP
        IF EXISTS (SELECT 1 FROM pg_class WHERE relname = r.old_nm AND relkind = 'i') THEN
            EXECUTE format('ALTER INDEX %I RENAME TO %I', r.old_nm, r.new_nm);
        END IF;
    END LOOP;

    -- -----------------------------------------------------------------
    -- (3) 입고 잔여수명 하한 — prod.ib_life_rate
    -- -----------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'prod' AND column_name = 'ib_life_rate') THEN
        ALTER TABLE prod ADD COLUMN ib_life_rate SMALLINT;
        ALTER TABLE prod ADD CONSTRAINT ck_prod_ib_life_rate
            CHECK (ib_life_rate IS NULL OR ib_life_rate BETWEEN 0 AND 100);
        -- 유통기한 관리 상품에만 기본 기준. 출고 기준(40%)보다 높아야 정상이다.
        UPDATE prod SET ib_life_rate = 70 WHERE shelf_life_days IS NOT NULL;
        COMMENT ON COLUMN prod.ib_life_rate IS
            '입고 허용 잔여수명 비율(%). 검수 시 (유통기한-입고일자)/shelf_life_days 가 이 비율 미만이면 차단. NULL = 미적용';
        RAISE NOTICE 'prod.ib_life_rate 추가';
    END IF;

    -- -----------------------------------------------------------------
    -- (4) 로케이션 용량 상한 — loc.max_qty
    --     STORAGE 는 적치 전략의 전제라 값이 반드시 있어야 한다.
    --     기존 행을 채운 뒤에야 제약을 걸 수 있다.
    -- -----------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'loc' AND column_name = 'max_qty') THEN
        ALTER TABLE loc ADD COLUMN max_qty BIGINT;
        UPDATE loc SET max_qty = 1000 WHERE loc_typ = 'STORAGE';
        ALTER TABLE loc ADD CONSTRAINT ck_loc_max_qty
            CHECK (max_qty IS NULL OR max_qty > 0);
        ALTER TABLE loc ADD CONSTRAINT ck_loc_storage_capacity
            CHECK (loc_typ <> 'STORAGE' OR max_qty IS NOT NULL);
        RAISE NOTICE 'loc.max_qty 추가';
    END IF;

    -- -----------------------------------------------------------------
    -- (5) 적치 지시 — putaway_task
    -- -----------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS putaway_task (
        putaway_task_id BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
        ib_line_id      BIGINT      NOT NULL,
        lot_id          BIGINT      NOT NULL,
        to_loc_id       BIGINT      NOT NULL,
        drct_qty        BIGINT      NOT NULL,
        cmpl_qty        BIGINT      DEFAULT 0 NOT NULL,
        status          VARCHAR(15) NOT NULL,
        cmpl_dt         TIMESTAMP,
        created_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
        created_by      VARCHAR(30) DEFAULT 'admin' NOT NULL,
        updated_at      TIMESTAMP,
        updated_by      VARCHAR(30),
        CONSTRAINT ck_ptwy_task_status CHECK (status IN ('DIRECTED', 'DONE', 'CANCELLED')),
        CONSTRAINT ck_ptwy_task_qty CHECK (drct_qty > 0 AND cmpl_qty >= 0 AND cmpl_qty <= drct_qty)
    );
    CREATE INDEX IF NOT EXISTS ix_ptwy_task_line ON putaway_task (ib_line_id);
    CREATE INDEX IF NOT EXISTS ix_ptwy_task_open_loc ON putaway_task (to_loc_id) WHERE status = 'DIRECTED';

    -- -----------------------------------------------------------------
    -- (6) 벤더 마스터 + ib_order.vendor_id 소급
    --     기존 ib_order.vndr_nm 의 distinct 값으로 벤더를 만들어 연결한다.
    --     표기가 흔들린 값도 한 건씩 올라온다 — 적용 후 벤더 화면에서 us_yn='N' 으로 정리할 것.
    -- -----------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS vendor (
        vendor_id   BIGINT          GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
        vndr_cd     VARCHAR(30)     NOT NULL,
        vndr_nm     VARCHAR(100)    NOT NULL,
        pic_nm      VARCHAR(50),
        tel_no      VARCHAR(30),
        us_yn       CHAR(1)         DEFAULT 'Y' NOT NULL,
        created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP NOT NULL,
        created_by  VARCHAR(30)     DEFAULT 'admin' NOT NULL,
        updated_at  TIMESTAMP,
        updated_by  VARCHAR(30),
        CONSTRAINT uq_vndr_cd UNIQUE (vndr_cd),
        CONSTRAINT ck_vendor_us_yn CHECK (us_yn IN ('Y', 'N'))
    );
    CREATE SEQUENCE IF NOT EXISTS vndr_cd_seq START WITH 1 INCREMENT BY 1;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'ib_order' AND column_name = 'vendor_id') THEN

        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'ib_order' AND column_name = 'vndr_nm') THEN
            INSERT INTO vendor (vndr_cd, vndr_nm)
            SELECT 'VD-' || LPAD(nextval('vndr_cd_seq')::text, 4, '0'), t.vndr_nm
              FROM (SELECT DISTINCT vndr_nm FROM ib_order ORDER BY vndr_nm) t
             WHERE NOT EXISTS (SELECT 1 FROM vendor v WHERE v.vndr_nm = t.vndr_nm);
        END IF;

        ALTER TABLE ib_order ADD COLUMN vendor_id BIGINT;
        UPDATE ib_order i SET vendor_id = v.vendor_id
          FROM vendor v WHERE v.vndr_nm = i.vndr_nm;
        -- ib_order 가 비어 있으면 벤더도 안 생기고 NOT NULL 도 무리 없이 걸린다.
        -- 데이터가 있는데 매칭 안 된 행이 있으면 여기서 멈추는 것이 맞다.
        ALTER TABLE ib_order ALTER COLUMN vendor_id SET NOT NULL;
        RAISE NOTICE 'ib_order.vendor_id 추가 및 소급 연결';
    END IF;
    CREATE INDEX IF NOT EXISTS ix_ib_order_vendor ON ib_order (vendor_id);

    -- -----------------------------------------------------------------
    -- (7) OMS 입고주문 + 기존 ASN 소급
    --     이미 창고로 나간 예정이므로 주문 상태는 CONVERTED 로 만든다.
    -- -----------------------------------------------------------------
    CREATE TABLE IF NOT EXISTS oms_ib_order (
        oms_ib_order_id BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
        oms_ib_no    VARCHAR(30)    NOT NULL,
        status       VARCHAR(15)    NOT NULL,
        vendor_id    BIGINT         NOT NULL,
        expct_de     DATE           NOT NULL,
        converted_at TIMESTAMP,
        created_at   TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
        created_by   VARCHAR(30)    DEFAULT 'admin' NOT NULL,
        updated_at   TIMESTAMP,
        updated_by   VARCHAR(30),
        CONSTRAINT uq_oms_ib_no UNIQUE (oms_ib_no),
        CONSTRAINT ck_oms_ib_order_status CHECK (status IN ('CREATED', 'CONVERTED', 'CANCELLED'))
    );
    CREATE SEQUENCE IF NOT EXISTS oms_ib_no_seq START WITH 1 INCREMENT BY 1;

    CREATE TABLE IF NOT EXISTS oms_ib_line (
        oms_ib_line_id  BIGINT      GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
        oms_ib_order_id BIGINT      NOT NULL,
        prod_id         BIGINT      NOT NULL,
        odr_qty         BIGINT      NOT NULL,
        created_at      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
        created_by      VARCHAR(30) DEFAULT 'admin' NOT NULL,
        updated_at      TIMESTAMP,
        updated_by      VARCHAR(30),
        CONSTRAINT ck_oms_ib_line_qty CHECK (odr_qty > 0)
    );
    CREATE INDEX IF NOT EXISTS ix_oms_ib_line_order ON oms_ib_line (oms_ib_order_id);
    CREATE INDEX IF NOT EXISTS ix_oms_ib_order_vendor ON oms_ib_order (vendor_id);

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'ib_order' AND column_name = 'oms_ib_order_id') THEN

        ALTER TABLE ib_order ADD COLUMN oms_ib_order_id BIGINT;

        FOR r IN SELECT ib_order_id, vendor_id, expct_de, created_at
                   FROM ib_order ORDER BY ib_order_id LOOP
            INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, converted_at)
                VALUES ('PO-' || TO_CHAR(r.expct_de, 'YYYYMMDD') || '-'
                              || LPAD(nextval('oms_ib_no_seq')::text, 3, '0'),
                        'CONVERTED', r.vendor_id, r.expct_de, r.created_at)
                RETURNING oms_ib_order_id INTO v_oms_id;

            INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
                SELECT v_oms_id, prod_id, expct_qty FROM ib_line WHERE ib_order_id = r.ib_order_id;

            UPDATE ib_order SET oms_ib_order_id = v_oms_id WHERE ib_order_id = r.ib_order_id;
        END LOOP;

        ALTER TABLE ib_order ALTER COLUMN oms_ib_order_id SET NOT NULL;
        RAISE NOTICE 'ib_order.oms_ib_order_id 추가 및 상위 주문 소급 생성';
    END IF;

    -- 주문 하나에 유효한 ASN은 하나. 취소분은 행으로 남으므로 통짜 UNIQUE 는 못 쓴다.
    CREATE UNIQUE INDEX IF NOT EXISTS uq_ib_order_oms_active
        ON ib_order (oms_ib_order_id) WHERE status <> 'CANCELLED';
    CREATE INDEX IF NOT EXISTS ix_ib_order_oms ON ib_order (oms_ib_order_id);

    -- -----------------------------------------------------------------
    -- (8) 텍스트 벤더명 제거 — 위 소급들이 매칭 키로 쓰므로 가장 마지막
    -- -----------------------------------------------------------------
    ALTER TABLE ib_order DROP COLUMN IF EXISTS vndr_nm;

    RAISE NOTICE '=== catch-up 완료 ===';
END
$catchup$;


-- =====================================================================
-- 적용 후 확인 (위가 성공한 뒤 따로 돌릴 것)
--   1) 테이블 18개인가
--      SELECT count(*) FROM information_schema.tables
--       WHERE table_schema = current_schema() AND table_type = 'BASE TABLE';
--
--   2) 옛 컬럼명이 남았나 — 0건이어야 한다
--      SELECT table_name, column_name FROM information_schema.columns
--       WHERE table_schema = current_schema()
--         AND column_name IN ('temp_zone','zone_cd','loc_type','tx_type','ref_doc_type',
--             'ref_doc_no','cancels_inv_hist_id','pick_prty','picked_qty','ptwy_qty',
--             'alloc_qty','wave_no','wave_id','group_cd','group_nm','use_yn','description',
--             'sort_ord','mgr_nm','order_qty','order_dt','expct_dt','closed_at',
--             'completed_at','shipped_at','sku_id','vndr_nm');
--
--   3) FK 0건인가
--      SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
--       WHERE c.contype='f' AND t.relnamespace = current_schema()::regnamespace;
-- =====================================================================
