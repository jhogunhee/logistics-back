-- 출고주문 시드 (2026-08-03 작성 / 2026-08-03 OMS 2계층 도입에 맞춰 개정)
--   seed-dev.sql + docs/migration-oms-outb.sql 이 적용된 DB에 DBeaver로 실행한다.
--   재실행 안전 — 이미 있는 SO/OB 번호는 건너뛴다.
--
-- 출고주문은 이제 OMS 원장(oms_outb_order)에서 시작해 확정으로 창고 문서(outb_order)가 된다.
-- 그래서 시드도 2단이다 — ① 주문 9건을 넣고 ② 그중 8건을 「확정」해 창고 문서를 만든다.
-- 확정된 8건은 전부 CREATED · 미편성이라 웨이브 편성 화면의 「미편성 주문」에 그대로 뜬다.
--
-- 출고유형(NRML/RTNGS)과 편수(1/2/3/미정)를 일부러 섞었다. 이 둘이 웨이브 전략의
-- 편성 조건 기준값이라, 값이 한 가지뿐이면 전략을 만들어도 「전부 편입 / 전부 제외」밖에 못 본다.
-- 배차 미정(NULL)이 한 건 있는 것도 의도한 것이다 — 편수 조건은 등가 비교라 NULL은 어떤 편수에도
-- 걸리지 않고 부정 연산자(≠ · NOT IN)에만 참이 된다.
--
--   SO-20260803-001 → OB-20260803-001  강남점    NRML  1편    상온 3품목
--   SO-20260803-002 → OB-20260803-002  판교점    NRML  1편    냉장 2품목
--   SO-20260803-003 → OB-20260803-003  수원점    NRML  2편    상온·냉장 3품목
--   SO-20260803-004 → OB-20260803-004  일산점    NRML  2편    냉동 2품목
--   SO-20260803-005 → OB-20260803-005  급식센터  NRML  3편    상온 2품목
--   SO-20260803-006 → OB-20260803-006  강남점    RTNGS 1편    반품 1품목
--   SO-20260803-007 → OB-20260803-007  수원점    RTNGS (미정) 반품 2품목
--   SO-20260804-001 → OB-20260804-001  판교점    NRML  1편    익일분 — 예정일 범위 필터 확인용
--   SO-20260805-001    (미확정)         강남점    NRML  2편    출고주문 관리 화면의 「작성」 상태 확인용

-- =====================================================================
-- ① OMS 출고주문 (원장)
-- =====================================================================

WITH new_order AS (
    INSERT INTO oms_outb_order (oms_outb_no, status, store_id, outb_typ, vhcl_fltno, expct_de, pic_nm, cfm_dt)
    SELECT 'SO-20260803-001', 'CONFIRMED', s.store_id, 'NRML', '1', DATE '2026-08-03', '김상현', CURRENT_TIMESTAMP
    FROM store s WHERE s.store_cd = 'ST-0001'
      AND NOT EXISTS (SELECT 1 FROM oms_outb_order o WHERE o.oms_outb_no = 'SO-20260803-001')
    RETURNING oms_outb_order_id
)
INSERT INTO oms_outb_line (oms_outb_order_id, prod_id, odr_qty)
SELECT new_order.oms_outb_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('제주 삼다수 2L', 120),
    ('신라면 멀티팩 (5입)', 60),
    ('햇반 백미 210g', 90)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_outb_order (oms_outb_no, status, store_id, outb_typ, vhcl_fltno, expct_de, pic_nm, cfm_dt)
    SELECT 'SO-20260803-002', 'CONFIRMED', s.store_id, 'NRML', '1', DATE '2026-08-03', '김상현', CURRENT_TIMESTAMP
    FROM store s WHERE s.store_cd = 'ST-0002'
      AND NOT EXISTS (SELECT 1 FROM oms_outb_order o WHERE o.oms_outb_no = 'SO-20260803-002')
    RETURNING oms_outb_order_id
)
INSERT INTO oms_outb_line (oms_outb_order_id, prod_id, odr_qty)
SELECT new_order.oms_outb_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('서울우유 1L', 48),
    ('참치마요 삼각김밥', 80)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_outb_order (oms_outb_no, status, store_id, outb_typ, vhcl_fltno, expct_de, pic_nm, cfm_dt)
    SELECT 'SO-20260803-003', 'CONFIRMED', s.store_id, 'NRML', '2', DATE '2026-08-03', '박지원', CURRENT_TIMESTAMP
    FROM store s WHERE s.store_cd = 'ST-0003'
      AND NOT EXISTS (SELECT 1 FROM oms_outb_order o WHERE o.oms_outb_no = 'SO-20260803-003')
    RETURNING oms_outb_order_id
)
INSERT INTO oms_outb_line (oms_outb_order_id, prod_id, odr_qty)
SELECT new_order.oms_outb_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('제주 삼다수 2L', 200),
    ('국산콩 두부 300g', 60),
    ('딸기 요거트 4입', 40)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_outb_order (oms_outb_no, status, store_id, outb_typ, vhcl_fltno, expct_de, pic_nm, cfm_dt)
    SELECT 'SO-20260803-004', 'CONFIRMED', s.store_id, 'NRML', '2', DATE '2026-08-03', '박지원', CURRENT_TIMESTAMP
    FROM store s WHERE s.store_cd = 'ST-0004'
      AND NOT EXISTS (SELECT 1 FROM oms_outb_order o WHERE o.oms_outb_no = 'SO-20260803-004')
    RETURNING oms_outb_order_id
)
INSERT INTO oms_outb_line (oms_outb_order_id, prod_id, odr_qty)
SELECT new_order.oms_outb_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('왕교자 만두 1kg', 30),
    ('붕어싸만코 (아이스크림)', 50)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_outb_order (oms_outb_no, status, store_id, outb_typ, vhcl_fltno, expct_de, pic_nm, cfm_dt)
    SELECT 'SO-20260803-005', 'CONFIRMED', s.store_id, 'NRML', '3', DATE '2026-08-03', '박지원', CURRENT_TIMESTAMP
    FROM store s WHERE s.store_cd = 'ST-0005'
      AND NOT EXISTS (SELECT 1 FROM oms_outb_order o WHERE o.oms_outb_no = 'SO-20260803-005')
    RETURNING oms_outb_order_id
)
INSERT INTO oms_outb_line (oms_outb_order_id, prod_id, odr_qty)
SELECT new_order.oms_outb_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('햇반 백미 210g', 150),
    ('일회용 종이컵 1000입', 20)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

-- 반품출고 — 출고유형 조건으로 따로 묶이는지 확인용
WITH new_order AS (
    INSERT INTO oms_outb_order (oms_outb_no, status, store_id, outb_typ, vhcl_fltno, expct_de, rmk, cfm_dt)
    SELECT 'SO-20260803-006', 'CONFIRMED', s.store_id, 'RTNGS', '1', DATE '2026-08-03', '유통기한 임박 반품', CURRENT_TIMESTAMP
    FROM store s WHERE s.store_cd = 'ST-0001'
      AND NOT EXISTS (SELECT 1 FROM oms_outb_order o WHERE o.oms_outb_no = 'SO-20260803-006')
    RETURNING oms_outb_order_id
)
INSERT INTO oms_outb_line (oms_outb_order_id, prod_id, odr_qty)
SELECT new_order.oms_outb_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('딸기 요거트 4입', 12)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

-- 배차 미정(vhcl_fltno NULL) — 편수 조건에 어떤 값으로도 걸리지 않는 주문
WITH new_order AS (
    INSERT INTO oms_outb_order (oms_outb_no, status, store_id, outb_typ, vhcl_fltno, expct_de, cfm_dt)
    SELECT 'SO-20260803-007', 'CONFIRMED', s.store_id, 'RTNGS', NULL, DATE '2026-08-03', CURRENT_TIMESTAMP
    FROM store s WHERE s.store_cd = 'ST-0003'
      AND NOT EXISTS (SELECT 1 FROM oms_outb_order o WHERE o.oms_outb_no = 'SO-20260803-007')
    RETURNING oms_outb_order_id
)
INSERT INTO oms_outb_line (oms_outb_order_id, prod_id, odr_qty)
SELECT new_order.oms_outb_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('신라면 멀티팩 (5입)', 15),
    ('서울우유 1L', 6)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

-- 익일분 — 실행 범위(대상 출고예정일)를 좁혔을 때 빠지는지 확인용
WITH new_order AS (
    INSERT INTO oms_outb_order (oms_outb_no, status, store_id, outb_typ, vhcl_fltno, expct_de, cfm_dt)
    SELECT 'SO-20260804-001', 'CONFIRMED', s.store_id, 'NRML', '1', DATE '2026-08-04', CURRENT_TIMESTAMP
    FROM store s WHERE s.store_cd = 'ST-0002'
      AND NOT EXISTS (SELECT 1 FROM oms_outb_order o WHERE o.oms_outb_no = 'SO-20260804-001')
    RETURNING oms_outb_order_id
)
INSERT INTO oms_outb_line (oms_outb_order_id, prod_id, odr_qty)
SELECT new_order.oms_outb_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('제주 삼다수 2L', 60),
    ('참치마요 삼각김밥', 40)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

-- 미확정(작성) 1건 — 출고주문 관리 화면에서 확정·수정·삭제를 눌러볼 대상.
-- 확정 전이라 창고에는 아무것도 생기지 않는다 (웨이브 후보에도 뜨지 않는다).
WITH new_order AS (
    INSERT INTO oms_outb_order (oms_outb_no, status, store_id, outb_typ, vhcl_fltno, expct_de, pic_nm, rmk)
    SELECT 'SO-20260805-001', 'CREATED', s.store_id, 'NRML', '2', DATE '2026-08-05', '김상현', '오전 도착 요청'
    FROM store s WHERE s.store_cd = 'ST-0001'
      AND NOT EXISTS (SELECT 1 FROM oms_outb_order o WHERE o.oms_outb_no = 'SO-20260805-001')
    RETURNING oms_outb_order_id
)
INSERT INTO oms_outb_line (oms_outb_order_id, prod_id, odr_qty)
SELECT new_order.oms_outb_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('제주 삼다수 2L', 80),
    ('국산콩 두부 300g', 24)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

-- =====================================================================
-- ② 확정 — 창고 출고주문(outb_order) 생성
--    화면의 「주문확정」이 하는 일을 SQL로 재현한다: 헤더를 복사하고(주문일은 등록일,
--    출고번호는 출고예정일 기준 채번) 라인을 1:1로 옮긴다. 수량 환산은 없다.
-- =====================================================================

WITH src AS (
    SELECT m.oms_outb_order_id, m.store_id, m.outb_typ, m.vhcl_fltno, m.expct_de,
           m.created_at::date AS odr_de,
           -- 그 날짜에 이미 있는 건수만큼 밀어서 번호를 잡는다 (일부만 확정된 상태에서 다시 돌려도 안 겹치게)
           'OB-' || to_char(m.expct_de, 'YYYYMMDD') || '-' ||
           lpad(((SELECT count(*) FROM outb_order o2 WHERE o2.expct_de = m.expct_de)
                 + row_number() OVER (PARTITION BY m.expct_de ORDER BY m.oms_outb_no))::text, 3, '0')
               AS outb_no
      FROM oms_outb_order m
     WHERE m.status = 'CONFIRMED'
       AND NOT EXISTS (SELECT 1 FROM outb_order o WHERE o.oms_outb_order_id = m.oms_outb_order_id)
)
INSERT INTO outb_order (outb_no, oms_outb_order_id, status, store_id, outb_typ, vhcl_fltno, odr_de, expct_de)
SELECT src.outb_no, src.oms_outb_order_id, 'CREATED', src.store_id, src.outb_typ, src.vhcl_fltno,
       src.odr_de, src.expct_de
FROM src;

INSERT INTO outb_line (outb_order_id, prod_id, odr_qty)
SELECT o.outb_order_id, l.prod_id, l.odr_qty
FROM outb_order o
JOIN oms_outb_line l ON l.oms_outb_order_id = o.oms_outb_order_id
WHERE NOT EXISTS (SELECT 1 FROM outb_line x WHERE x.outb_order_id = o.outb_order_id);

-- 채번 카운터를 날짜별 건수만큼 맞춘다. 안 하면 화면에서 같은 날짜로 등록·확정할 때
-- 001부터 다시 채번돼 uq_oms_outb_no · uq_outb_no 유니크 위반이 난다.
INSERT INTO nbr_seq (rule_cd, dync_ky, seq) VALUES
    ('OMS_OUTB_NO', '20260803', 7),
    ('OMS_OUTB_NO', '20260804', 1),
    ('OMS_OUTB_NO', '20260805', 1),
    ('OUTB_NO', '20260803', 7),
    ('OUTB_NO', '20260804', 1)
ON CONFLICT (rule_cd, dync_ky) DO UPDATE SET seq = GREATEST(nbr_seq.seq, EXCLUDED.seq);

-- 확인:
--   SELECT m.oms_outb_no, m.status, m.expct_de, m.outb_typ, m.vhcl_fltno, o.outb_no, o.status
--     FROM oms_outb_order m LEFT JOIN outb_order o ON o.oms_outb_order_id = m.oms_outb_order_id
--    ORDER BY m.oms_outb_no;
