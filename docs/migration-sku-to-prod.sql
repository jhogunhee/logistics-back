-- =====================================================================
-- sku → prod 개명 delta (PostgreSQL / Supabase)
--   근거: docs/naming-dictionary.md 「상품 = PROD (Product)」
--
--   업무 용어를 "SKU"에서 "상품"으로 통일하면서, 표준 단어 사전의 약어를 따라
--   테이블·컬럼·시퀀스·제약·인덱스를 일괄 개명한다. 구조 변경은 없다 —
--   이름만 바뀌므로 데이터는 그대로 살아 있고 되돌리기도 대칭적이다.
--
--   적용 순서: 이 파일은 기존 migration-*.sql 을 모두 적용한 뒤 맨 마지막에 돌린다.
--   앞선 증분들은 그 시점의 이름(sku)으로 작성돼 있으므로 수정하지 않았다 —
--   과거 스크립트를 지금 이름으로 고치면 아직 적용 전인 DB에서 순서가 깨진다.
--
--   코드보다 먼저 적용해야 한다. 반대 순서면 엔티티가 기대하는 prod_* 가 DB에 없어
--   부팅이 깨진다.
--
--   실행: Supabase 대시보드 › SQL Editor 에 붙여넣고 Run
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- (A) 테이블 · 컬럼 개명
--     PK는 {테이블명}_id, FK 컬럼은 참조 테이블 PK명을 그대로 쓴다는 규칙에 따라
--     sku_id 를 들고 있는 6개 테이블이 함께 바뀐다.
-- ---------------------------------------------------------------------

-- 이미 개명된 DB에 다시 돌려도 안전하도록 존재를 확인하고 넘어간다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relname = 'sku' AND c.relkind = 'r' AND n.nspname = current_schema()) THEN
        ALTER TABLE sku RENAME TO prod;
        RAISE NOTICE 'sku → prod 개명 완료';
    ELSIF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                   WHERE c.relname = 'prod' AND c.relkind = 'r' AND n.nspname = current_schema()) THEN
        RAISE NOTICE 'prod 가 이미 있다 — 개명은 끝난 상태다. 건너뛴다';
    ELSE
        RAISE EXCEPTION 'sku 도 prod 도 없다. 스키마(%)에 docs/schema.sql 이 적용되지 않았거나 접속 스키마가 다르다', current_schema();
    END IF;
END $$;

DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            ('sku_id', 'prod_id'), ('sku_cd', 'prod_cd'), ('sku_nm', 'prod_nm')
        ) AS v(old_nm, new_nm)
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'prod' AND column_name = r.old_nm) THEN
            EXECUTE format('ALTER TABLE prod RENAME COLUMN %I TO %I', r.old_nm, r.new_nm);
        END IF;
    END LOOP;
END $$;

-- 남는 것 하나: prod_id 의 identity 내부 시퀀스는 생성 당시 이름(sku_sku_id_seq)을
-- 그대로 유지한다. Postgres 가 자동 생성·관리하는 객체라 코드나 스키마가 이름으로
-- 참조하지 않으므로 동작에 영향이 없어 건드리지 않았다.
-- (업무 코드 채번용 sku_cd_seq 는 우리가 직접 이름으로 부르므로 아래 (B)에서 바꾼다)

-- 참조 측. oms_ib_line 은 migration-oms-inbound.sql 이 만드는 테이블이라
-- 아직 없을 수 있다 — 테이블 단위로 존재를 확인하고 건너뛴다.
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['lot', 'ib_line', 'oms_ib_line', 'inv', 'inv_hist', 'outb_line']
    LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = t AND column_name = 'sku_id'
        ) THEN
            EXECUTE format('ALTER TABLE %I RENAME COLUMN sku_id TO prod_id', t);
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- (B) 시퀀스
--     PROD-0001 형식 업무 코드 채번 전용. PK identity 와는 분리 운용이므로
--     현재값(last_value)이 그대로 따라와야 채번이 이어진다 — RENAME 은 값을 보존한다.
-- ---------------------------------------------------------------------

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relname = 'sku_cd_seq' AND c.relkind = 'S' AND n.nspname = current_schema()) THEN
        ALTER SEQUENCE sku_cd_seq RENAME TO prod_cd_seq;
    ELSIF NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                       WHERE c.relname = 'prod_cd_seq' AND c.relkind = 'S' AND n.nspname = current_schema()) THEN
        -- 이 DB는 과거에 sku_cd_seq 가 통째로 없어서 만들어 넣은 이력이 있다.
        -- 없으면 채번이 죽으므로 여기서 만들어 둔다. START WITH 는 기존 최대 채번값 + 1.
        RAISE NOTICE 'sku_cd_seq 도 prod_cd_seq 도 없다 — 새로 만든다';
        EXECUTE 'CREATE SEQUENCE prod_cd_seq START WITH 1 INCREMENT BY 1';
        PERFORM setval('prod_cd_seq',
                       GREATEST(1, COALESCE((SELECT MAX(NULLIF(regexp_replace(prod_cd, '\D', '', 'g'), '')::bigint)
                                               FROM prod), 0)));
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- (C) 제약 · 인덱스
--     FK는 migration-drop-fks.sql 에서 이미 전부 제거됐으므로 대상이 없다.
--     남은 것은 UNIQUE 와 CHECK — CLAUDE.md 가 약화 금지로 못박은 것들이라
--     drop/recreate 하지 않고 이름만 바꾼다.
-- ---------------------------------------------------------------------

-- 이 DB는 과거에 schema.sql 과 드리프트가 있었던 이력이 있다(sku_cd_seq 누락).
-- 제약·인덱스가 이름대로 존재하지 않을 수 있으므로 존재를 확인하고 넘어간다 —
-- 여기서 멈추면 위의 컬럼 개명까지 통째로 롤백된다.
-- ck_sku_ib_life_rate 는 migration-inbound-putaway.sql 이, 나머지는 schema.sql 이 만든다.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
            ('uq_sku_cd',           'uq_prod_cd'),
            ('ck_sku_temp_zone',    'ck_prod_temp_zone'),
            ('ck_sku_ib_life_rate', 'ck_prod_ib_life_rate')
        ) AS v(old_nm, new_nm)
    LOOP
        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = r.old_nm) THEN
            EXECUTE format('ALTER TABLE prod RENAME CONSTRAINT %I TO %I', r.old_nm, r.new_nm);
        ELSE
            RAISE NOTICE '제약 % 이(가) 없어 건너뜀 — DB 드리프트 여부를 확인할 것', r.old_nm;
        END IF;
    END LOOP;

    FOR r IN
        SELECT * FROM (VALUES
            ('ix_lot_sku_receipt',  'ix_lot_prod_receipt'),
            ('ix_inv_sku',          'ix_inv_prod'),
            ('ix_invh_sku_created', 'ix_invh_prod_created')
        ) AS v(old_nm, new_nm)
    LOOP
        IF EXISTS (SELECT 1 FROM pg_class WHERE relname = r.old_nm AND relkind = 'i') THEN
            EXECUTE format('ALTER INDEX %I RENAME TO %I', r.old_nm, r.new_nm);
        ELSE
            RAISE NOTICE '인덱스 % 이(가) 없어 건너뜀 — DB 드리프트 여부를 확인할 것', r.old_nm;
        END IF;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- (D) 주석 재작성
--     COMMENT 는 개명을 따라오지 않고 본문의 "SKU" 표기도 그대로 남으므로 다시 쓴다.
--     문구는 docs/schema.sql 과 일치시킨다.
-- ---------------------------------------------------------------------

COMMENT ON TABLE  prod IS '상품 마스터. 보관 규칙(온도대)과 유통기한 정책을 상품 단위로 정의';
COMMENT ON COLUMN prod.prod_cd         IS '상품 코드 (업무 식별자, 예: PROD-0001)';
COMMENT ON COLUMN prod.temp_zone       IS '보관 온도대 (DRY 상온 / CHL 냉장 / FRZ 냉동). 적치·이동 시 로케이션 온도대와 일치 검증';
COMMENT ON COLUMN prod.shelf_life_days IS '제조일 기준 총 유통기한(일). NULL = 유통기한 미관리(공산품 등). 시더가 Lot 유통기한 생성 시 사용';

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'prod' AND column_name = 'ib_life_rate'
    ) THEN
        COMMENT ON COLUMN prod.ib_life_rate IS '입고 허용 잔여수명 비율(%). 검수 시 (유통기한-입고일자)/shelf_life_days 가 이 비율 미만이면 차단. 기준을 벤더가 아니라 상품에 두는 이유는 같은 벤더가 유통기한 3일짜리와 1년짜리를 함께 납품하기 때문. NULL = 미적용';
    END IF;
END $$;

COMMENT ON COLUMN loc.temp_zone  IS '존 온도대. 상품 온도대와 불일치하면 적치·이동 차단 (스테이징은 예외적으로 전 온도대 허용할지 서비스에서 판단)';
COMMENT ON COLUMN lot.lot_no     IS 'Lot 번호 (상품 내 유일). 입고일 기준 상품별 일자 리셋 채번(LOT-YYMMDD-NNN). 유통기한 미관리 상품도 동일 형식이며 입고일자만으로 구분됨';
COMMENT ON COLUMN lot.receipt_dt IS '입고일자 (소급 등록 가능). 상품+입고일자+제조일자가 같으면 기존 Lot을 재사용 (증분 검수 시 배치 중복 생성 방지). 유통기한 미관리 상품은 제조일자가 항상 NULL이라 사실상 상품+입고일자로만 구분';
COMMENT ON COLUMN lot.mfg_dt     IS '제조일자. 유통기한 미관리 상품의 Lot은 NULL';
COMMENT ON COLUMN lot.expiry_dt  IS '유통기한. 생성 시점의 prod.shelf_life_days로 계산해 저장한 스냅샷 (이후 상품 마스터 변경에 소급 영향 없음). NULL = 미관리 상품의 Lot (FEFO 맨 뒤 정렬, 잔여수명 필터 대상 아님)';
COMMENT ON TABLE  inv            IS '현재고 스냅샷. 키: 상품+Loc+Lot. 가용재고 = on_hand - alloc (파생값, 컬럼 아님)';

COMMIT;


-- =====================================================================
-- (E) [선택] 업무 코드 값 SKU-0001 → PROD-0001
--
--     여기까지가 스키마 개명이고, 아래는 이미 쌓인 **데이터 값**을 바꾼다.
--     성격이 다르므로 트랜잭션을 분리했다 — 위만 적용하고 코드 값은 SKU- 로
--     두어도 앱은 정상 동작한다(prod_cd 는 그냥 문자열이다).
--
--     다만 화면 라벨이 "상품 코드"인데 값이 SKU-0001 로 보이므로, 실사용 이력이
--     없는 개발 DB라면 함께 바꾸는 쪽을 권한다.
--
--     주의: 외부에 이미 배포된 코드가 있거나 지류/라벨에 인쇄된 값이 있으면
--     적용하지 말 것. prod_cd 는 업무 식별자라 바꾸면 대외 참조가 끊긴다.
--     inv_hist.ref_doc_no 등 코드 값을 문자열로 들고 있는 컬럼은 없으므로
--     이 UPDATE 하나로 끝난다.
--
--     적용하려면 아래 블록의 주석을 풀고 실행한다.
-- =====================================================================

-- BEGIN;
--
-- UPDATE prod
--    SET prod_cd = 'PROD-' || substring(prod_cd from 5)
--  WHERE prod_cd LIKE 'SKU-%';
--
-- COMMIT;


-- =====================================================================
-- 적용 후 확인
--   1) 개명이 끝났는지 — 0건이어야 한다
--      SELECT table_name, column_name FROM information_schema.columns
--       WHERE column_name LIKE '%sku%';
--
--   2) 상품 건수가 보존됐는지 — 개명 전과 같아야 한다
--      SELECT COUNT(*) FROM prod;
--
--   3) 채번 시퀀스가 현재값을 유지했는지 — 기존 최대 채번값 이상이어야 한다
--      SELECT last_value FROM prod_cd_seq;
--
--   4) 참조 컬럼이 전부 따라왔는지 — 6건이어야 한다 (oms_ib_line 미적용 시 5건)
--      SELECT table_name FROM information_schema.columns
--       WHERE column_name = 'prod_id' AND table_name <> 'prod';
-- =====================================================================
