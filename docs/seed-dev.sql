-- 개발용 시드 데이터 (식품/음료 유통센터 컨셉) — PostgreSQL / Supabase, 현행 기준 문서
--   docs/schema.sql 을 먼저 적용한 뒤 실행한다.
-- prod_cd는 백엔드 채번과 충돌하지 않도록 반드시 prod_cd_seq로 발급한다.
-- shelf_life_days NULL = 유통기한 미관리 (공산품, 유통기한 표시 면제 품목 등)
-- 실행: Supabase SQL Editor 또는 `psql ... -f seed-dev.sql` (UTF-8)

-- 상온(DRY)
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '제주 삼다수 2L', 'DRY', 365);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '신라면 멀티팩 (5입)', 'DRY', 180);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '햇반 백미 210g', 'DRY', 270);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '일회용 종이컵 1000입', 'DRY', NULL);

-- 냉장(CHL)
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '서울우유 1L', 'CHL', 14);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '딸기 요거트 4입', 'CHL', 21);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '참치마요 삼각김밥', 'CHL', 2);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '국산콩 두부 300g', 'CHL', 14);

-- 냉동(FRZ)
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '왕교자 만두 1kg', 'FRZ', 365);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '냉동 새우살 500g', 'FRZ', 540);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '붕어싸만코 (아이스크림)', 'FRZ', NULL);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '코카콜라 350ml (24입)', 'DRY', 365);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '진라면 순한맛 멀티팩 (5입)', 'DRY', 240);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '백설 밀가루 1kg', 'DRY', 540);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '스팸 클래식 200g', 'DRY', 1095);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '물티슈 캡형 100매', 'DRY', NULL);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '바나나우유 240ml', 'CHL', 12);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '슬라이스 치즈 20매', 'CHL', 60);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '닭가슴살 샐러드', 'CHL', 3);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '모짜렐라 피자치즈 1kg', 'FRZ', 365);
INSERT INTO prod (prod_cd, prod_nm, temp_zone, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '냉동 블루베리 1kg', 'FRZ', 720);

-- 입고 허용 잔여수명 기준(%). 유통기한 관리 상품에만 붙인다 (미관리 상품은 NULL = 미적용).
-- 출고 기준(store.outb_life_rate 기본 40%)보다 높게 잡는 것이 정상이다 —
-- 받을 때 이미 40%면 점포에 내보낼 수 있는 창이 남지 않는다.
UPDATE prod SET ib_life_rate = 70 WHERE shelf_life_days IS NOT NULL;

-- 로케이션 (입고 스테이징 1 + 출고 스테이징 1 + 온도대별 보관 로케이션)
-- 스테이징의 temp_zone은 플레이스홀더(DRY) — 반출/적치 지점이라 온도 제약은 서비스에서 스킵
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('RCV-STAGE',   'RCV-STAGE', 'DRY', 'STAGE',   0);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('SHIP-STAGE',  'SHIP-STAGE','DRY', 'STAGE',   0);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('DRY-A-01-01', 'DRY',       'DRY', 'STORAGE', 1);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('DRY-A-01-02', 'DRY',       'DRY', 'STORAGE', 2);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('DRY-A-02-01', 'DRY',       'DRY', 'STORAGE', 3);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('DRY-B-01-01', 'DRY',       'DRY', 'STORAGE', 4);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('CHL-A-01-01', 'CHL',       'CHL', 'STORAGE', 1);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('CHL-A-01-02', 'CHL',       'CHL', 'STORAGE', 2);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('CHL-B-01-01', 'CHL',       'CHL', 'STORAGE', 3);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('FRZ-A-01-01', 'FRZ',       'FRZ', 'STORAGE', 1);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('FRZ-A-01-02', 'FRZ',       'FRZ', 'STORAGE', 2);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('DRY-B-01-02', 'DRY',       'DRY', 'STORAGE', 5);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('DRY-C-01-01', 'DRY',       'DRY', 'STORAGE', 6);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('CHL-B-01-02', 'CHL',       'CHL', 'STORAGE', 4);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('FRZ-B-01-01', 'FRZ',       'FRZ', 'STORAGE', 3);
INSERT INTO loc (loc_cd, zone_cd, temp_zone, loc_type, pick_prty) VALUES ('FRZ-B-01-02', 'FRZ',       'FRZ', 'STORAGE', 4);

-- 최대 적재 수량. 스테이징은 받는 대로 쌓이는 곳이라 NULL(무제한)로 둔다.
-- 온도대별 1순위 로케이션만 일부러 작게 잡아, 적치 지시가 한 배치를 여러 로케이션으로
-- 1:N 분할하는 경로가 시드 데이터만으로도 재현되게 한다 (전부 넉넉하면 분할이 안 나온다).
UPDATE loc SET max_qty = 1000 WHERE loc_type = 'STORAGE';
UPDATE loc SET max_qty = 300  WHERE loc_type = 'STORAGE' AND pick_prty = 1;

-- 벤더 (입고 거래처). 코드는 VendorService의 채번 규칙(VD-0001)을 따른다.
INSERT INTO vendor (vndr_cd, vndr_nm, mgr_nm, tel_no) VALUES ('VD-0001', '서울식품',       '김상현', '02-1234-5601');
INSERT INTO vendor (vndr_cd, vndr_nm, mgr_nm, tel_no) VALUES ('VD-0002', '콜드체인프레시', '이정민', '031-555-0102');
INSERT INTO vendor (vndr_cd, vndr_nm, mgr_nm, tel_no) VALUES ('VD-0003', '대한물류',       '박도현', '02-9876-5403');
INSERT INTO vendor (vndr_cd, vndr_nm, mgr_nm, tel_no) VALUES ('VD-0004', '한마음유통',     '최유진', '031-777-0204');
INSERT INTO vendor (vndr_cd, vndr_nm, mgr_nm, tel_no) VALUES ('VD-0005', '그린푸드',       '정성호', '02-4321-0505');
-- 거래 종료 벤더 1건 — 신규 주문 벤더 선택에서 걸러지는지 확인용
INSERT INTO vendor (vndr_cd, vndr_nm, mgr_nm, tel_no, use_yn) VALUES ('VD-0006', '옛거래처식자재', '한지훈', '02-1111-2222', 'N');

-- 시퀀스를 시드 건수만큼 밀어둔다. 안 하면 화면에서 벤더를 처음 등록할 때
-- VD-0001부터 다시 채번돼 uq_vndr_cd 유니크 위반이 난다.
SELECT setval('vndr_cd_seq', 6);

-- 점포 (납품 허용 잔여수명 비율: 편의점 > 마트 > 급식 — FEFO 앞단 필터 시나리오용)
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0001', '씨앤유 편의점 강남점', 70);
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0002', '씨앤유 편의점 판교점', 70);
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0003', '한마음마트 수원점', 50);
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0004', '한마음마트 일산점', 40);
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0005', '행복급식센터', 30);

COMMIT;

-- =====================================================================
-- 입고주문(OMS) → 변환 시 입고예정(ASN) 생성
--
-- ASN을 직접 INSERT하지 않는 이유: ib_order.oms_ib_order_id가 NOT NULL이라 상위 주문 없이는 못 만든다.
-- 생성된 ASN은 전부 SCHEDULED — 검수/마감은 화면에서 진행해야 재고 불변식(이력 합계=스냅샷)이 지켜진다.
--
-- Oracle의 DECLARE/BEGIN...RETURNING INTO 익명 블록 대신, 헤더 INSERT를 CTE로 두고
-- 그 RETURNING 결과에 라인 INSERT를 SELECT로 이어붙이는 방식으로 헤더+라인을 한 문장에서 처리한다.
-- =====================================================================

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_dt)
    SELECT 'PO-20260717-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-17'
    FROM vendor v WHERE v.vndr_cd = 'VD-0001'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, order_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.order_qty
FROM new_order
CROSS JOIN (VALUES
    ('서울우유 1L', 50),
    ('딸기 요거트 4입', 40),
    ('참치마요 삼각김밥', 30)
) AS v(prod_nm, order_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_dt)
    SELECT 'PO-20260717-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-17'
    FROM vendor v WHERE v.vndr_cd = 'VD-0002'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, order_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.order_qty
FROM new_order
CROSS JOIN (VALUES
    ('왕교자 만두 1kg', 80),
    ('냉동 새우살 500g', 60),
    ('붕어싸만코 (아이스크림)', 120)
) AS v(prod_nm, order_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_dt)
    SELECT 'PO-20260718-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-18'
    FROM vendor v WHERE v.vndr_cd = 'VD-0003'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, order_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.order_qty
FROM new_order
CROSS JOIN (VALUES
    ('제주 삼다수 2L', 300),
    ('햇반 백미 210g', 200),
    ('일회용 종이컵 1000입', 100)
) AS v(prod_nm, order_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_dt)
    SELECT 'PO-20260719-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-19'
    FROM vendor v WHERE v.vndr_cd = 'VD-0004'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, order_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.order_qty
FROM new_order
CROSS JOIN (VALUES
    ('스팸 클래식 200g', 150),
    ('바나나우유 240ml', 60)
) AS v(prod_nm, order_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

-- 미변환(CREATED)으로 남길 주문 1건 — 화면에서 'ASN 변환' 버튼을 눌러보기 위한 시드.
-- 예정일이 07-20이라 아래 변환 대상 조건(< 2026-07-20)에서 빠진다.
WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_dt)
    SELECT 'PO-20260720-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-20'
    FROM vendor v WHERE v.vndr_cd = 'VD-0005'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, order_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.order_qty
FROM new_order
CROSS JOIN (VALUES
    ('서울우유 1L', 100),
    ('햇반 백미 210g', 80)
) AS v(prod_nm, order_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;


-- 변환. OmsIbOrderService.convert()와 같은 일을 한 문장에서 한다
-- (ASN 헤더 생성 → 라인 복사 → 주문 상태 전이).
-- WITH 안의 데이터 변경문은 참조되지 않아도 반드시 실행되므로 copied를 따로 읽지 않아도 된다.
-- 라인 복사가 oms_ib_line을 읽을 수 있는 건 그게 앞선 문장에서 이미 커밋됐기 때문이다.
WITH to_convert AS (
    SELECT oms_ib_order_id, vendor_id, expct_dt
    FROM oms_ib_order
    WHERE status = 'CREATED' AND expct_dt < DATE '2026-07-20'
),
new_asn AS (
    INSERT INTO ib_order (ib_no, oms_ib_order_id, status, vendor_id, expct_dt)
    SELECT 'IB-' || to_char(expct_dt, 'YYYYMMDD') || '-' || lpad(nextval('ib_no_seq')::text, 3, '0'),
           oms_ib_order_id, 'SCHEDULED', vendor_id, expct_dt
    FROM to_convert
    RETURNING ib_order_id, oms_ib_order_id
),
copied AS (
    INSERT INTO ib_line (ib_order_id, prod_id, expct_qty)
    SELECT a.ib_order_id, l.prod_id, l.order_qty
    FROM new_asn a
    JOIN oms_ib_line l ON l.oms_ib_order_id = a.oms_ib_order_id
    RETURNING ib_line_id
)
UPDATE oms_ib_order o
SET status = 'CONVERTED', converted_at = CURRENT_TIMESTAMP
WHERE o.oms_ib_order_id IN (SELECT oms_ib_order_id FROM to_convert);
