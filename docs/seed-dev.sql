-- 개발용 시드 데이터 (식품/음료 유통센터 컨셉) — PostgreSQL / Supabase, 현행 기준 문서
--   docs/schema.sql 을 먼저 적용한 뒤 실행한다.
-- prod_cd는 백엔드 채번과 충돌하지 않도록 반드시 prod_cd_seq로 발급한다.
-- shelf_life_days NULL = 유통기한 미관리 (공산품, 유통기한 표시 면제 품목 등)
-- 실행: Supabase SQL Editor 또는 `psql ... -f seed-dev.sql` (UTF-8)

-- 입고단위 = 벤더에게 발주하고 납품받는 단위, 출고단위 = 재고 저장 단위(점포로 나가는 단위).
--   PLT  : 회전이 빠른 대량 공산품만 (생수 · 콜라 · 라면 · 종이컵). 파렛트는 보통 적재 형태이지
--          발주 단위가 아니라서, 실제로 파렛트로 발주하는 품목에만 붙였다.
--   TRAY : 유통기한이 2~3일인 데일리 신선 (삼각김밥 · 샐러드) — 소량 다빈도 납품.
--   BOX  : 그 외 전부. 케이스 발주가 벤더 거래의 표준이다.
-- 출고단위가 BOX인 것은 음료 2건뿐이다 (점포에 박스째 나간다). 나머지는 낱개 피킹이라 EA.

-- 상온(DRY)
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '제주 삼다수 2L', 'DRY', 'PLT', 'BOX', 365);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '신라면 멀티팩 (5입)', 'DRY', 'PLT', 'EA', 180);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '햇반 백미 210g', 'DRY', 'BOX', 'EA', 270);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '일회용 종이컵 1000입', 'DRY', 'PLT', 'EA', NULL);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '코카콜라 350ml (24입)', 'DRY', 'PLT', 'BOX', 365);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '진라면 순한맛 멀티팩 (5입)', 'DRY', 'BOX', 'EA', 240);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '백설 밀가루 1kg', 'DRY', 'BOX', 'EA', 540);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '스팸 클래식 200g', 'DRY', 'BOX', 'EA', 1095);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '물티슈 캡형 100매', 'DRY', 'BOX', 'EA', NULL);

-- 냉장(CHL)
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '서울우유 1L', 'CHL', 'BOX', 'EA', 14);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '딸기 요거트 4입', 'CHL', 'BOX', 'EA', 21);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '참치마요 삼각김밥', 'CHL', 'TRAY', 'EA', 2);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '국산콩 두부 300g', 'CHL', 'BOX', 'EA', 14);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '바나나우유 240ml', 'CHL', 'BOX', 'EA', 12);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '슬라이스 치즈 20매', 'CHL', 'BOX', 'EA', 60);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '닭가슴살 샐러드', 'CHL', 'TRAY', 'EA', 3);

-- 냉동(FRZ)
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '왕교자 만두 1kg', 'FRZ', 'BOX', 'EA', 365);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '냉동 새우살 500g', 'FRZ', 'BOX', 'EA', 540);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '붕어싸만코 (아이스크림)', 'FRZ', 'BOX', 'EA', NULL);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '모짜렐라 피자치즈 1kg', 'FRZ', 'BOX', 'EA', 365);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-' || lpad(nextval('prod_cd_seq')::text, 4, '0'), '냉동 블루베리 1kg', 'FRZ', 'BOX', 'EA', 720);

-- 상품 포장. 위 상품이 inb_uom_cd · outb_uom_cd로 가리키는 단위는 반드시 여기 있어야 한다
-- (ProdService가 지키는 규칙이고, 없으면 ASN 변환이 IllegalStateException으로 죽는다).
-- 출고단위를 낱개수량 1의 기준으로 잡았다 — 환산은 ea_qty(입고) / ea_qty(출고)로 파생된다.
-- wgt는 포장재 무게를 포함한 실측 중량(kg)이고, NULL은 미측정이다 (물티슈·블루베리 2건).
INSERT INTO prod_uom (prod_id, uom_cd, ea_qty, wgt)
SELECT p.prod_id, v.uom_cd, v.ea_qty, v.wgt
FROM prod p
JOIN (VALUES
    -- 삼다수·코카콜라는 낱개 → 박스 → 파렛트 3단이다. 입고/출고단위가 아닌 포장도 목록에 있을 수
    -- 있다는 것(파렛트는 적재 계획용)을 시드에서 보여준다 — 환산은 입고·출고단위 둘만 쓴다.
    ('제주 삼다수 2L',            'EA',     1,   2.050),
    ('제주 삼다수 2L',            'BOX',    6,  12.400),
    ('제주 삼다수 2L',            'PLT',  384, 800.000),
    ('신라면 멀티팩 (5입)',        'EA',     1,  0.120),
    ('신라면 멀티팩 (5입)',        'BOX',    5,  0.650),   -- 발주는 박스, 재고는 낱개
    ('햇반 백미 210g',            'EA',     1,  0.215),
    ('일회용 종이컵 1000입',       'EA',     1,  0.005),
    ('일회용 종이컵 1000입',       'BOX', 1000,  5.400),
    ('서울우유 1L',               'EA',     1,  1.032),
    ('서울우유 1L',               'TRAY',  12, 12.500),
    ('딸기 요거트 4입',            'EA',     1,  0.085),
    ('딸기 요거트 4입',            'PACK',   4,  0.360),
    ('참치마요 삼각김밥',           'EA',     1,  0.105),
    ('국산콩 두부 300g',           'EA',     1,  0.310),
    ('왕교자 만두 1kg',            'EA',     1,  1.020),
    ('냉동 새우살 500g',           'EA',     1,  0.510),
    ('붕어싸만코 (아이스크림)',      'EA',     1,  0.150),
    ('코카콜라 350ml (24입)',      'EA',     1,   0.365),
    ('코카콜라 350ml (24입)',      'BOX',   24,   9.120),
    ('코카콜라 350ml (24입)',      'PLT',  2400, 920.000),
    ('진라면 순한맛 멀티팩 (5입)',   'EA',     1,  0.120),
    ('진라면 순한맛 멀티팩 (5입)',   'BOX',    5,  0.640),
    ('백설 밀가루 1kg',            'EA',     1,  1.010),
    ('스팸 클래식 200g',           'EA',     1,  0.215),
    ('물티슈 캡형 100매',          'EA',     1,  NULL),
    ('바나나우유 240ml',           'EA',     1,  0.250),
    ('슬라이스 치즈 20매',          'EA',     1,  0.360),
    ('닭가슴살 샐러드',            'EA',     1,  0.230),
    ('모짜렐라 피자치즈 1kg',       'EA',     1,  1.010),
    ('냉동 블루베리 1kg',          'EA',     1,  NULL)
) AS v(prod_nm, uom_cd, ea_qty, wgt) ON v.prod_nm = p.prod_nm;

-- 존 (로케이션의 상위 그룹). loc.zon_cd가 참조하므로 로케이션보다 먼저 넣는다.
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RCV-STAGE',  '입고 스테이징', 'DRY', 'VRTL', 'INB');
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('SHIP-STAGE', '출고 스테이징', 'DRY', 'VRTL', 'OUTB');
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('DRY',        '상온 보관존',   'DRY', 'RACK', 'STRG');
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('CHL',        '냉장 보관존',   'CHL', 'RACK', 'STRG');
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('FRZ',        '냉동 보관존',   'FRZ', 'RACK', 'STRG');

-- 로케이션 (입고 스테이징 1 + 출고 스테이징 1 + 온도대별 보관 로케이션)
-- 스테이징의 temp_zone은 플레이스홀더(DRY) — 반출/적치 지점이라 온도 제약은 서비스에서 스킵
-- 최대 적재 수량: STORAGE는 ck_loc_storage_capacity가 NOT NULL을 강제하므로 INSERT 시점에 채운다.
-- 스테이징은 받는 대로 쌓이는 곳이라 NULL(무제한)로 둔다.
-- 온도대별 1순위 로케이션만 일부러 작게(300) 잡아, 적치 지시가 한 배치를 여러 로케이션으로
-- 1:N 분할하는 경로가 시드 데이터만으로도 재현되게 한다 (전부 넉넉하면 분할이 안 나온다).
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('RCV-STAGE', 'RCV-STAGE', 'DRY', 'STAGE', 0, NULL);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('SHIP-STAGE', 'SHIP-STAGE', 'DRY', 'STAGE', 0, NULL);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-A-01-01', 'DRY', 'DRY', 'STORAGE', 1, 300);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-A-01-02', 'DRY', 'DRY', 'STORAGE', 2, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-A-02-01', 'DRY', 'DRY', 'STORAGE', 3, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-B-01-01', 'DRY', 'DRY', 'STORAGE', 4, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('CHL-A-01-01', 'CHL', 'CHL', 'STORAGE', 1, 300);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('CHL-A-01-02', 'CHL', 'CHL', 'STORAGE', 2, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('CHL-B-01-01', 'CHL', 'CHL', 'STORAGE', 3, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('FRZ-A-01-01', 'FRZ', 'FRZ', 'STORAGE', 1, 300);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('FRZ-A-01-02', 'FRZ', 'FRZ', 'STORAGE', 2, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-B-01-02', 'DRY', 'DRY', 'STORAGE', 5, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-C-01-01', 'DRY', 'DRY', 'STORAGE', 6, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('CHL-B-01-02', 'CHL', 'CHL', 'STORAGE', 4, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('FRZ-B-01-01', 'FRZ', 'FRZ', 'STORAGE', 3, 1000);
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('FRZ-B-01-02', 'FRZ', 'FRZ', 'STORAGE', 4, 1000);

-- 벤더 (입고 거래처). 코드는 VendorService의 채번 규칙(VD-0001)을 따른다.
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0001', '서울식품', '김상현', '02-1234-5601');
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0002', '콜드체인프레시', '이정민', '031-555-0102');
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0003', '대한물류', '박도현', '02-9876-5403');
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0004', '한마음유통', '최유진', '031-777-0204');
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0005', '그린푸드', '정성호', '02-4321-0505');
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0006', '옛거래처식자재', '한지훈', '02-1111-2222');

-- 시퀀스를 시드 건수만큼 밀어둔다. 안 하면 화면에서 벤더를 처음 등록할 때
-- VD-0001부터 다시 채번돼 uq_vndr_cd 유니크 위반이 난다.
SELECT setval('vndr_cd_seq', 6);

-- 점포 (납품 허용 잔여수명 비율: 편의점 > 마트 > 급식 — FEFO 앞단 필터 시나리오용)
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0001', '씨앤유 편의점 강남점', 70);
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0002', '씨앤유 편의점 판교점', 70);
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0003', '한마음마트 수원점', 50);
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0004', '한마음마트 일산점', 40);
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0005', '행복급식센터', 30);

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
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de)
    SELECT 'PO-20260717-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-17'
    FROM vendor v WHERE v.vndr_cd = 'VD-0001'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('서울우유 1L', 50),
    ('딸기 요거트 4입', 40),
    ('참치마요 삼각김밥', 30)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de)
    SELECT 'PO-20260717-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-17'
    FROM vendor v WHERE v.vndr_cd = 'VD-0002'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('왕교자 만두 1kg', 80),
    ('냉동 새우살 500g', 60),
    ('붕어싸만코 (아이스크림)', 120)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de)
    SELECT 'PO-20260718-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-18'
    FROM vendor v WHERE v.vndr_cd = 'VD-0003'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('제주 삼다수 2L', 300),
    ('햇반 백미 210g', 200),
    ('일회용 종이컵 1000입', 100)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de)
    SELECT 'PO-20260719-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-19'
    FROM vendor v WHERE v.vndr_cd = 'VD-0004'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('스팸 클래식 200g', 150),
    ('바나나우유 240ml', 60)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

-- 미변환(CREATED)으로 남길 주문 1건 — 화면에서 'ASN 변환' 버튼을 눌러보기 위한 시드.
-- 예정일이 07-20이라 아래 변환 대상 조건(< 2026-07-20)에서 빠진다.
WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de)
    SELECT 'PO-20260720-' || lpad(nextval('oms_ib_no_seq')::text, 3, '0'), 'CREATED', v.vendor_id, DATE '2026-07-20'
    FROM vendor v WHERE v.vndr_cd = 'VD-0005'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('서울우유 1L', 100),
    ('햇반 백미 210g', 80)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;


-- 변환. OmsIbOrderService.convert()와 같은 일을 한 문장에서 한다
-- (ASN 헤더 생성 → 라인 복사 → 주문 상태 전이).
-- WITH 안의 데이터 변경문은 참조되지 않아도 반드시 실행되므로 copied를 따로 읽지 않아도 된다.
-- 라인 복사가 oms_ib_line을 읽을 수 있는 건 그게 앞선 문장에서 이미 커밋됐기 때문이다.
WITH to_convert AS (
    SELECT oms_ib_order_id, vendor_id, expct_de
    FROM oms_ib_order
    WHERE status = 'CREATED' AND expct_de < DATE '2026-07-20'
),
new_asn AS (
    INSERT INTO ib_order (ib_no, oms_ib_order_id, status, vendor_id, expct_de)
    SELECT 'IB-' || to_char(expct_de, 'YYYYMMDD') || '-' || lpad(nextval('ib_no_seq')::text, 3, '0'),
           oms_ib_order_id, 'SCHEDULED', vendor_id, expct_de
    FROM to_convert
    RETURNING ib_order_id, oms_ib_order_id
),
copied AS (
    INSERT INTO ib_line (ib_order_id, prod_id, expct_qty)
    -- 발주 수량은 입고단위, ASN 예정 수량은 출고단위다 — OmsIbOrderService.convert()와 같은 환산을
    -- 여기서도 한다 (Prod.toOutbQty: odr_qty × 낱개수량(입고단위) / 낱개수량(출고단위)).
    SELECT a.ib_order_id, l.prod_id, l.odr_qty * i.ea_qty / o.ea_qty
    FROM new_asn a
    JOIN oms_ib_line l ON l.oms_ib_order_id = a.oms_ib_order_id
    JOIN prod p ON p.prod_id = l.prod_id
    JOIN prod_uom i ON i.prod_id = p.prod_id AND i.uom_cd = p.inb_uom_cd
    JOIN prod_uom o ON o.prod_id = p.prod_id AND o.uom_cd = p.outb_uom_cd
    RETURNING ib_line_id
)
UPDATE oms_ib_order o
SET status = 'CONVERTED', converted_at = CURRENT_TIMESTAMP
WHERE o.oms_ib_order_id IN (SELECT oms_ib_order_id FROM to_convert);
