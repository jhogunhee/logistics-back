-- 개발용 시드 데이터 (식품/음료 유통센터 컨셉) — PostgreSQL / Supabase, 현행 기준 문서
--   docs/schema.sql 을 먼저 적용한 뒤 실행한다.
-- prod_cd는 이제 리터럴로 직접 넣는다. 화면에서 처음 등록할 때 PROD-0001부터
-- 다시 채번돼 uq_prod_cd 위반이 나지 않도록, 아래에서 nbr_seq를 시드 건수만큼 맞춰둔다.
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
    VALUES ('PROD-0001', '제주 삼다수 2L', 'DRY', 'PLT', 'BOX', 365);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0002', '신라면 멀티팩 (5입)', 'DRY', 'PLT', 'EA', 180);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0003', '햇반 백미 210g', 'DRY', 'BOX', 'EA', 270);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0004', '일회용 종이컵 1000입', 'DRY', 'PLT', 'EA', NULL);

-- 냉장(CHL)
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0005', '서울우유 1L', 'CHL', 'BOX', 'EA', 14);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0006', '딸기 요거트 4입', 'CHL', 'BOX', 'EA', 21);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0007', '참치마요 삼각김밥', 'CHL', 'TRAY', 'EA', 2);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0008', '국산콩 두부 300g', 'CHL', 'BOX', 'EA', 14);

-- 냉동(FRZ)
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0009', '왕교자 만두 1kg', 'FRZ', 'BOX', 'EA', 365);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0010', '냉동 새우살 500g', 'FRZ', 'BOX', 'EA', 540);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0011', '붕어싸만코 (아이스크림)', 'FRZ', 'BOX', 'EA', NULL);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0012', '코카콜라 350ml (24입)', 'DRY', 'PLT', 'BOX', 365);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0013', '진라면 순한맛 멀티팩 (5입)', 'DRY', 'BOX', 'EA', 240);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0014', '백설 밀가루 1kg', 'DRY', 'BOX', 'EA', 540);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0015', '스팸 클래식 200g', 'DRY', 'BOX', 'EA', 1095);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0016', '물티슈 캡형 100매', 'DRY', 'BOX', 'EA', NULL);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0017', '바나나우유 240ml', 'CHL', 'BOX', 'EA', 12);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0018', '슬라이스 치즈 20매', 'CHL', 'BOX', 'EA', 60);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0019', '닭가슴살 샐러드', 'CHL', 'TRAY', 'EA', 3);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0020', '모짜렐라 피자치즈 1kg', 'FRZ', 'BOX', 'EA', 365);
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days)
    VALUES ('PROD-0021', '냉동 블루베리 1kg', 'FRZ', 'BOX', 'EA', 720);

-- 상품 포장. 위 상품이 inb_uom_cd · outb_uom_cd로 가리키는 단위는 반드시 여기 있어야 한다
-- (ProdService가 지키는 규칙이고, 없으면 ASN 생성이 IllegalStateException으로 죽는다).
--
-- ea_qty는 「이 단위 1개 = 낱개 몇 개」이고, 환산은 ea_qty(입고) / ea_qty(출고)로 파생된다.
-- 낱개(EA)를 매개로 삼기 때문에 EA 행은 언제나 1이고 모든 상품이 갖는다.
-- 입고단위 ea_qty는 출고단위 ea_qty로 나누어떨어져야 한다 — 안 그러면 toOutbQty의 정수
-- 나눗셈이 수량을 조용히 깎는다 (삼다수 504 % 6 = 0, 콜라 1440 % 24 = 0, 나머지는 출고가 EA).
--
-- 파렛트 행은 입고단위인 4건에만 둔다. 파렛트 적재수 = 박스 적재수 × 박스 입수다
-- (삼다수 84박스 × 6 = 504, 콜라 60박스 × 24 = 1440, 신라면 60박스 × 8 = 480, 종이컵 40박스 × 4 = 160).
--
-- wgt는 포장재 무게(tare)를 포함한 실측 중량(kg)이고 NULL은 미측정이다 — 물티슈 박스와
-- 블루베리 박스 2건을 미측정으로 남겨 화면의 「미측정」 표시 경로가 시드만으로 재현되게 한다.
INSERT INTO prod_uom (prod_id, uom_cd, ea_qty, wgt)
SELECT p.prod_id, v.uom_cd, v.ea_qty, v.wgt
FROM prod p
JOIN (VALUES
    -- 상온 — 파렛트 발주 4건 (생수 · 라면 · 종이컵 · 콜라)
    ('제주 삼다수 2L',            'EA',     1,    2.050),
    ('제주 삼다수 2L',            'BOX',    6,   12.400),
    ('제주 삼다수 2L',            'PLT',  504, 1050.000),   -- 발주는 파렛트, 재고는 박스
    ('신라면 멀티팩 (5입)',        'EA',     1,    0.600),
    ('신라면 멀티팩 (5입)',        'BOX',    8,    4.950),
    ('신라면 멀티팩 (5입)',        'PLT',  480,  305.000),   -- 가벼워서 파렛트당 박스가 많다
    ('일회용 종이컵 1000입',       'EA',     1,    5.400),   -- 낱개가 이미 1000입 팩이다
    ('일회용 종이컵 1000입',       'BOX',    4,   22.000),
    ('일회용 종이컵 1000입',       'PLT',  160,  890.000),
    ('코카콜라 350ml (24입)',      'EA',     1,    0.365),
    ('코카콜라 350ml (24입)',      'BOX',   24,    9.120),
    ('코카콜라 350ml (24입)',      'PLT', 1440,  560.000),
    ('진라면 순한맛 멀티팩 (5입)',   'EA',     1,    0.600),
    ('진라면 순한맛 멀티팩 (5입)',   'BOX',    8,    4.950),
    ('햇반 백미 210g',            'EA',     1,    0.215),
    ('햇반 백미 210g',            'BOX',   24,    5.400),
    ('백설 밀가루 1kg',            'EA',     1,    1.010),
    ('백설 밀가루 1kg',            'BOX',   12,   12.400),
    ('스팸 클래식 200g',           'EA',     1,    0.215),
    ('스팸 클래식 200g',           'BOX',   24,    5.400),
    ('물티슈 캡형 100매',          'EA',     1,    0.520),
    ('물티슈 캡형 100매',          'BOX',   10,     NULL),   -- 미측정
    -- 냉장 — 데일리 신선 2건은 트레이 납품
    ('서울우유 1L',               'EA',     1,    1.032),
    ('서울우유 1L',               'BOX',   12,   12.600),
    ('딸기 요거트 4입',            'EA',     1,    0.360),   -- 낱개가 4입 한 팩이다
    ('딸기 요거트 4입',            'BOX',   12,    4.500),
    ('국산콩 두부 300g',           'EA',     1,    0.310),
    ('국산콩 두부 300g',           'BOX',   20,    6.500),
    ('바나나우유 240ml',           'EA',     1,    0.250),
    ('바나나우유 240ml',           'BOX',   24,    6.300),
    ('슬라이스 치즈 20매',          'EA',     1,    0.360),
    ('슬라이스 치즈 20매',          'BOX',   15,    5.700),
    ('참치마요 삼각김밥',           'EA',     1,    0.105),
    ('참치마요 삼각김밥',           'TRAY',  20,    2.300),   -- 유통기한 2일, 소량 다빈도
    ('닭가슴살 샐러드',            'EA',     1,    0.230),
    ('닭가슴살 샐러드',            'TRAY',  12,    3.000),
    -- 냉동
    ('왕교자 만두 1kg',            'EA',     1,    1.020),
    ('왕교자 만두 1kg',            'BOX',    8,    8.400),
    ('냉동 새우살 500g',           'EA',     1,    0.510),
    ('냉동 새우살 500g',           'BOX',   20,   10.500),
    ('붕어싸만코 (아이스크림)',      'EA',     1,    0.150),
    ('붕어싸만코 (아이스크림)',      'BOX',   24,    3.800),
    ('모짜렐라 피자치즈 1kg',       'EA',     1,    1.010),
    ('모짜렐라 피자치즈 1kg',       'BOX',   10,   10.500),
    ('냉동 블루베리 1kg',          'EA',     1,    1.010),
    ('냉동 블루베리 1kg',          'BOX',   10,     NULL)    -- 미측정
) AS v(prod_nm, uom_cd, ea_qty, wgt) ON v.prod_nm = p.prod_nm;

-- 존 (로케이션의 상위 그룹). loc.zon_cd가 참조하므로 로케이션보다 먼저 넣는다.
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RCV-STAGE',  '입고 스테이징', 'DRY', 'VRTL', 'INB') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('SHIP-STAGE', '출고 스테이징', 'DRY', 'VRTL', 'OUTB') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('DRY',        '상온 보관존',   'DRY', 'RACK', 'STRG') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('CHL',        '냉장 보관존',   'CHL', 'RACK', 'STRG') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('FRZ',        '냉동 보관존',   'FRZ', 'RACK', 'STRG') ON CONFLICT (zon_cd) DO NOTHING;

-- 로케이션 (입고 스테이징 1 + 출고 스테이징 1 + 온도대별 보관 로케이션)
-- 스테이징의 temp_zone은 플레이스홀더(DRY) — 반출/적치 지점이라 온도 제약은 서비스에서 스킵
-- 최대 적재 수량: STORAGE는 ck_loc_storage_capacity가 NOT NULL을 강제하므로 INSERT 시점에 채운다.
-- 스테이징은 받는 대로 쌓이는 곳이라 NULL(무제한)로 둔다.
-- 온도대별 1순위 로케이션만 일부러 작게(300) 잡아, 적치 지시가 한 배치를 여러 로케이션으로
-- 1:N 분할하는 경로가 시드 데이터만으로도 재현되게 한다 (전부 넉넉하면 분할이 안 나온다).
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('RCV-STAGE', 'RCV-STAGE', 'DRY', 'STAGE', 0, NULL) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('SHIP-STAGE', 'SHIP-STAGE', 'DRY', 'STAGE', 0, NULL) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-A-01-01', 'DRY', 'DRY', 'STORAGE', 1, 300) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-A-01-02', 'DRY', 'DRY', 'STORAGE', 2, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-A-02-01', 'DRY', 'DRY', 'STORAGE', 3, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-B-01-01', 'DRY', 'DRY', 'STORAGE', 4, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('CHL-A-01-01', 'CHL', 'CHL', 'STORAGE', 1, 300) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('CHL-A-01-02', 'CHL', 'CHL', 'STORAGE', 2, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('CHL-B-01-01', 'CHL', 'CHL', 'STORAGE', 3, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('FRZ-A-01-01', 'FRZ', 'FRZ', 'STORAGE', 1, 300) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('FRZ-A-01-02', 'FRZ', 'FRZ', 'STORAGE', 2, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-B-01-02', 'DRY', 'DRY', 'STORAGE', 5, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('DRY-C-01-01', 'DRY', 'DRY', 'STORAGE', 6, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('CHL-B-01-02', 'CHL', 'CHL', 'STORAGE', 4, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('FRZ-B-01-01', 'FRZ', 'FRZ', 'STORAGE', 3, 1000) ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_cd, tmp_zon, loc_typ, pikng_prty, max_qty)
VALUES ('FRZ-B-01-02', 'FRZ', 'FRZ', 'STORAGE', 4, 1000) ON CONFLICT (loc_cd) DO NOTHING;

-- 벤더 (입고 거래처). 코드는 VendorService의 채번 규칙(VD-0001)을 따른다.
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0001', '서울식품', '김상현', '02-1234-5601') ON CONFLICT (vndr_cd) DO NOTHING;
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0002', '콜드체인프레시', '이정민', '031-555-0102') ON CONFLICT (vndr_cd) DO NOTHING;
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0003', '대한물류', '박도현', '02-9876-5403') ON CONFLICT (vndr_cd) DO NOTHING;
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0004', '한마음유통', '최유진', '031-777-0204') ON CONFLICT (vndr_cd) DO NOTHING;
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0005', '그린푸드', '정성호', '02-4321-0505') ON CONFLICT (vndr_cd) DO NOTHING;
INSERT INTO vendor (vndr_cd, vndr_nm, pic_nm, tel_no)
VALUES ('VD-0006', '옛거래처식자재', '한지훈', '02-1111-2222') ON CONFLICT (vndr_cd) DO NOTHING;

-- 채번 카운터를 시드 건수만큼 미리 채운다. 안 하면 화면에서 처음 등록할 때
-- PROD-0001/VD-0001부터 다시 채번돼 uq_prod_cd/uq_vndr_cd 유니크 위반이 난다.
INSERT INTO nbr_seq (rule_cd, dync_ky, seq) VALUES
    ('PROD_CD', '-', 21),
    ('VNDR_CD', '-', 6)
ON CONFLICT (rule_cd, dync_ky) DO UPDATE SET seq = GREATEST(nbr_seq.seq, EXCLUDED.seq);

-- 점포 (납품 허용 잔여수명 비율: 편의점 > 마트 > 급식 — FEFO 앞단 필터 시나리오용)
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0001', '씨앤유 편의점 강남점', 70) ON CONFLICT (store_cd) DO NOTHING;
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0002', '씨앤유 편의점 판교점', 70) ON CONFLICT (store_cd) DO NOTHING;
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0003', '한마음마트 수원점', 50) ON CONFLICT (store_cd) DO NOTHING;
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0004', '한마음마트 일산점', 40) ON CONFLICT (store_cd) DO NOTHING;
INSERT INTO store (store_cd, store_nm, outb_life_rate)
VALUES ('ST-0005', '행복급식센터', 30) ON CONFLICT (store_cd) DO NOTHING;

COMMIT;

-- =====================================================================
-- 입고주문(OMS) → 확정 시 입고예정(ASN) 생성
--
-- ASN을 직접 INSERT하지 않는 이유: ib_order.oms_ib_order_id가 NOT NULL이라 상위 주문 없이는 못 만든다.
-- 생성된 ASN은 전부 SCHEDULED — 검수/마감은 화면에서 진행해야 재고 불변식(이력 합계=스냅샷)이 지켜진다.
--
-- Oracle의 DECLARE/BEGIN...RETURNING INTO 익명 블록 대신, 헤더 INSERT를 CTE로 두고
-- 그 RETURNING 결과에 라인 INSERT를 SELECT로 이어붙이는 방식으로 헤더+라인을 한 문장에서 처리한다.
-- =====================================================================

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260717-001', 'CREATED', v.vendor_id, DATE '2026-07-17', 'NRML', '김상현', '오전 도착 요청'
    FROM vendor v WHERE v.vndr_cd = 'VD-0001'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('서울우유 1L', 40),          -- 40 BOX  -> 480 EA
    ('딸기 요거트 4입', 30),       -- 30 BOX  -> 360 EA
    ('참치마요 삼각김밥', 15)      -- 15 TRAY -> 300 EA
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260717-002', 'CREATED', v.vendor_id, DATE '2026-07-17', 'URGT', '이정민', '냉동 결품 대응 — 우선 하차'
    FROM vendor v WHERE v.vndr_cd = 'VD-0002'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('왕교자 만두 1kg', 25),           -- 25 BOX -> 200 EA
    ('냉동 새우살 500g', 12),          -- 12 BOX -> 240 EA
    ('붕어싸만코 (아이스크림)', 20)     -- 20 BOX -> 480 EA (1순위 로케이션 300을 넘어 적치가 분할된다)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260718-001', 'CREATED', v.vendor_id, DATE '2026-07-18', 'NRML', '박도현', NULL
    FROM vendor v WHERE v.vndr_cd = 'VD-0003'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('제주 삼다수 2L', 2),             -- 2 PLT -> 168 BOX (PLT 504 / BOX 6)
    ('신라면 멀티팩 (5입)', 1),         -- 1 PLT -> 480 EA
    ('햇반 백미 210g', 20),            -- 20 BOX -> 480 EA
    ('일회용 종이컵 1000입', 1)        -- 1 PLT -> 160 EA
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260719-001', 'CREATED', v.vendor_id, DATE '2026-07-19', 'NRML', '최유진', '파렛트 상태 확인 후 하차'
    FROM vendor v WHERE v.vndr_cd = 'VD-0004'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('스팸 클래식 200g', 15),          -- 15 BOX -> 360 EA
    ('바나나우유 240ml', 10),          -- 10 BOX -> 240 EA
    ('코카콜라 350ml (24입)', 1)       -- 1 PLT -> 60 BOX (PLT 1440 / BOX 24)
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;

-- 미확정(CREATED)으로 남길 주문 1건 — 화면에서 '주문확정' 버튼을 눌러보기 위한 시드.
-- 예정일이 07-20이라 아래 확정 대상 조건(< 2026-07-20)에서 빠진다.
WITH new_order AS (
    INSERT INTO oms_ib_order (oms_ib_no, status, vendor_id, expct_de, odr_dvsn, pic_nm, rmk)
    SELECT 'PO-20260720-001', 'CREATED', v.vendor_id, DATE '2026-07-20', 'RTNGS', '정성호', '점포 반품분 재입고'
    FROM vendor v WHERE v.vndr_cd = 'VD-0005'
    RETURNING oms_ib_order_id
)
INSERT INTO oms_ib_line (oms_ib_order_id, prod_id, odr_qty)
SELECT new_order.oms_ib_order_id, prod.prod_id, v.odr_qty
FROM new_order
CROSS JOIN (VALUES
    ('서울우유 1L', 20),               -- 확정하면 240 EA
    ('햇반 백미 210g', 10)             -- 확정하면 240 EA
) AS v(prod_nm, odr_qty)
JOIN prod ON prod.prod_nm = v.prod_nm;


-- 확정. OmsIbOrderService.confirm()과 같은 일을 한 문장에서 한다
-- (ASN 헤더 생성 → 라인 복사 → 주문 상태 전이).
-- WITH 안의 데이터 변경문은 참조되지 않아도 반드시 실행되므로 copied를 따로 읽지 않아도 된다.
-- 라인 복사가 oms_ib_line을 읽을 수 있는 건 그게 앞선 문장에서 이미 커밋됐기 때문이다.
WITH to_convert AS (
    SELECT oms_ib_order_id, vendor_id, expct_de,
           row_number() OVER (PARTITION BY expct_de ORDER BY oms_ib_order_id) AS seq_in_date
    FROM oms_ib_order
    WHERE status = 'CREATED' AND expct_de < DATE '2026-07-20'
),
new_asn AS (
    INSERT INTO ib_order (ib_no, oms_ib_order_id, status, vendor_id, expct_de)
    SELECT 'IB-' || to_char(expct_de, 'YYYYMMDD') || '-' || lpad(seq_in_date::text, 3, '0'),
           oms_ib_order_id, 'SCHEDULED', vendor_id, expct_de
    FROM to_convert
    RETURNING ib_order_id, oms_ib_order_id
),
copied AS (
    INSERT INTO ib_line (ib_order_id, prod_id, expct_qty)
    -- 발주 수량은 입고단위, ASN 예정 수량은 낱개(EA)다 — OmsIbOrderService.confirm()과 같은 환산을
    -- 여기서도 한다 (Prod.toEaQty: odr_qty × 낱개수량(입고단위)).
    SELECT a.ib_order_id, l.prod_id, l.odr_qty * i.ea_qty
    FROM new_asn a
    JOIN oms_ib_line l ON l.oms_ib_order_id = a.oms_ib_order_id
    JOIN prod p ON p.prod_id = l.prod_id
    JOIN prod_uom i ON i.prod_id = p.prod_id AND i.uom_cd = p.inb_uom_cd
    RETURNING ib_line_id
)
UPDATE oms_ib_order o
SET status = 'CONFIRMED', cfm_dt = CURRENT_TIMESTAMP
WHERE o.oms_ib_order_id IN (SELECT oms_ib_order_id FROM to_convert);

-- OMS_IB_NO/IB_NO 채번 카운터도 시드 건수만큼 날짜별로 맞춘다. 07-17엔 2건, 나머지는 1건씩.
-- 07-20 예정 주문은 미확정 상태로 남으므로 IB_NO에는 07-20 행이 없다.
INSERT INTO nbr_seq (rule_cd, dync_ky, seq) VALUES
    ('OMS_IB_NO', '20260717', 2),
    ('OMS_IB_NO', '20260718', 1),
    ('OMS_IB_NO', '20260719', 1),
    ('OMS_IB_NO', '20260720', 1),
    ('IB_NO', '20260717', 2),
    ('IB_NO', '20260718', 1),
    ('IB_NO', '20260719', 1)
ON CONFLICT (rule_cd, dync_ky) DO UPDATE SET seq = GREATEST(nbr_seq.seq, EXCLUDED.seq);
