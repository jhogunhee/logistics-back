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
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0001', '제주 삼다수 2L', 'DRY', 'PLT', 'BOX', 365, 'emoji:💧');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0002', '신라면 멀티팩 (5입)', 'DRY', 'PLT', 'EA', 180, 'emoji:🍜');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0003', '햇반 백미 210g', 'DRY', 'BOX', 'EA', 270, 'emoji:🍚');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0004', '일회용 종이컵 1000입', 'DRY', 'PLT', 'EA', NULL, 'emoji:🍽️');

-- 냉장(CHL)
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0005', '서울우유 1L', 'CHL', 'BOX', 'EA', 14, 'emoji:🥛');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0006', '딸기 요거트 4입', 'CHL', 'BOX', 'EA', 21, 'emoji:🍓');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0007', '참치마요 삼각김밥', 'CHL', 'TRAY', 'EA', 2, 'emoji:🍙');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0008', '국산콩 두부 300g', 'CHL', 'BOX', 'EA', 14, 'emoji:🫘');

-- 냉동(FRZ)
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0009', '왕교자 만두 1kg', 'FRZ', 'BOX', 'EA', 365, 'emoji:🥟');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0010', '냉동 새우살 500g', 'FRZ', 'BOX', 'EA', 540, 'emoji:🍤');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0011', '붕어싸만코 (아이스크림)', 'FRZ', 'BOX', 'EA', NULL, 'emoji:🍦');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0012', '코카콜라 350ml (24입)', 'DRY', 'PLT', 'BOX', 365, 'emoji:🥤');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0013', '진라면 순한맛 멀티팩 (5입)', 'DRY', 'BOX', 'EA', 240, 'emoji:🍜');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0014', '백설 밀가루 1kg', 'DRY', 'BOX', 'EA', 540, 'emoji:🌾');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0015', '스팸 클래식 200g', 'DRY', 'BOX', 'EA', 1095, 'emoji:🥓');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0016', '물티슈 캡형 100매', 'DRY', 'BOX', 'EA', NULL, 'emoji:🧻');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0017', '바나나우유 240ml', 'CHL', 'BOX', 'EA', 12, 'emoji:🍌');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0018', '슬라이스 치즈 20매', 'CHL', 'BOX', 'EA', 60, 'emoji:🧀');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0019', '닭가슴살 샐러드', 'CHL', 'TRAY', 'EA', 3, 'emoji:🥗');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0020', '모짜렐라 피자치즈 1kg', 'FRZ', 'BOX', 'EA', 365, 'emoji:🍕');
INSERT INTO prod (prod_cd, prod_nm, tmp_zon, inb_uom_cd, outb_uom_cd, shelf_life_days, img_url)
    VALUES ('PROD-0021', '냉동 블루베리 1kg', 'FRZ', 'BOX', 'EA', 720, 'emoji:🫐');

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

-- 존 (로케이션의 상위 그룹). loc.zon_id가 참조하므로 로케이션보다 먼저 넣는다.
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
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'RCV-STAGE', zon_id, 'DRY', 'STAGE', 0, NULL FROM zon WHERE zon_cd = 'RCV-STAGE' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'SHIP-STAGE', zon_id, 'DRY', 'STAGE', 0, NULL FROM zon WHERE zon_cd = 'SHIP-STAGE' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'DRY-A-01-01', zon_id, 'DRY', 'STORAGE', 1, 300 FROM zon WHERE zon_cd = 'DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'DRY-A-01-02', zon_id, 'DRY', 'STORAGE', 2, 1000 FROM zon WHERE zon_cd = 'DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'DRY-A-02-01', zon_id, 'DRY', 'STORAGE', 3, 1000 FROM zon WHERE zon_cd = 'DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'DRY-B-01-01', zon_id, 'DRY', 'STORAGE', 4, 1000 FROM zon WHERE zon_cd = 'DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'CHL-A-01-01', zon_id, 'CHL', 'STORAGE', 1, 300 FROM zon WHERE zon_cd = 'CHL' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'CHL-A-01-02', zon_id, 'CHL', 'STORAGE', 2, 1000 FROM zon WHERE zon_cd = 'CHL' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'CHL-B-01-01', zon_id, 'CHL', 'STORAGE', 3, 1000 FROM zon WHERE zon_cd = 'CHL' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'FRZ-A-01-01', zon_id, 'FRZ', 'STORAGE', 1, 300 FROM zon WHERE zon_cd = 'FRZ' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'FRZ-A-01-02', zon_id, 'FRZ', 'STORAGE', 2, 1000 FROM zon WHERE zon_cd = 'FRZ' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'DRY-B-01-02', zon_id, 'DRY', 'STORAGE', 5, 1000 FROM zon WHERE zon_cd = 'DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'DRY-C-01-01', zon_id, 'DRY', 'STORAGE', 6, 1000 FROM zon WHERE zon_cd = 'DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'CHL-B-01-02', zon_id, 'CHL', 'STORAGE', 4, 1000 FROM zon WHERE zon_cd = 'CHL' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'FRZ-B-01-01', zon_id, 'FRZ', 'STORAGE', 3, 1000 FROM zon WHERE zon_cd = 'FRZ' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, max_qty)
SELECT 'FRZ-B-01-02', zon_id, 'FRZ', 'STORAGE', 4, 1000 FROM zon WHERE zon_cd = 'FRZ' ON CONFLICT (loc_cd) DO NOTHING;

-- 피킹존 (biz_dvsn = PIKNG). 보관 로케이션은 존과 온도대가 같아야 하므로 온도대별로 하나씩 둔다.
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('PIK-DRY', '상온 피킹존', 'DRY', 'RACK', 'PIKNG') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('PIK-CHL', '냉장 피킹존', 'CHL', 'RACK', 'PIKNG') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('PIK-FRZ', '냉동 피킹존', 'FRZ', 'RACK', 'PIKNG') ON CONFLICT (zon_cd) DO NOTHING;

-- 피킹 로케이션. pikng_prty 0 — 보관 로케이션(1~)보다 앞이라 FEFO 동순위에서 먼저 할당된다.
-- ptawy_prty 9 — 적치 동선의 후순위. max_qty는 피킹 페이스답게 작게(200).
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'PIK-DRY-01-01', zon_id, 'DRY', 'STORAGE', 0, 9, 200 FROM zon WHERE zon_cd = 'PIK-DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'PIK-DRY-01-02', zon_id, 'DRY', 'STORAGE', 0, 9, 200 FROM zon WHERE zon_cd = 'PIK-DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'PIK-CHL-01-01', zon_id, 'CHL', 'STORAGE', 0, 9, 200 FROM zon WHERE zon_cd = 'PIK-CHL' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'PIK-FRZ-01-01', zon_id, 'FRZ', 'STORAGE', 0, 9, 200 FROM zon WHERE zon_cd = 'PIK-FRZ' ON CONFLICT (loc_cd) DO NOTHING;

-- 반품존 (biz_dvsn = RTNGS). 반품 검수의 불량분이 바로 들어가 보류로 묶이는 자리 — 보류가 보관(STORAGE)
-- 로케이션만 받으므로 STORAGE다. 할당 후보에서는 존 업무구분으로 제외된다.
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-DRY', '상온 반품존', 'DRY', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-CHL', '냉장 반품존', 'CHL', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO zon (zon_cd, zon_nm, tmp_zon, strg_typ, biz_dvsn) VALUES ('RTN-FRZ', '냉동 반품존', 'FRZ', 'FLAT', 'RTNGS') ON CONFLICT (zon_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'RTN-DRY-01', zon_id, 'DRY', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-DRY' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'RTN-CHL-01', zon_id, 'CHL', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-CHL' ON CONFLICT (loc_cd) DO NOTHING;
INSERT INTO loc (loc_cd, zon_id, tmp_zon, loc_typ, pikng_prty, ptawy_prty, max_qty)
SELECT 'RTN-FRZ-01', zon_id, 'FRZ', 'STORAGE', 99, 99, 5000 FROM zon WHERE zon_cd = 'RTN-FRZ' ON CONFLICT (loc_cd) DO NOTHING;

-- 고정 로케이션 마스터 (상품×로케이션). uq_fxng_loc(loc_id)로 멱등 — 한 로케이션 = 한 상품 전용.
-- min/max는 보충 기준(미구현) — max는 loc.max_qty(200) 이하.
INSERT INTO fxng_loc (prod_id, loc_id, min_qty, max_qty)
SELECT p.prod_id, l.loc_id, 50, 200 FROM prod p, loc l
WHERE p.prod_nm = '신라면 멀티팩 (5입)' AND l.loc_cd = 'PIK-DRY-01-01' ON CONFLICT (loc_id) DO NOTHING;
INSERT INTO fxng_loc (prod_id, loc_id, min_qty, max_qty)
SELECT p.prod_id, l.loc_id, 30, 200 FROM prod p, loc l
WHERE p.prod_nm = '서울우유 1L' AND l.loc_cd = 'PIK-CHL-01-01' ON CONFLICT (loc_id) DO NOTHING;
INSERT INTO fxng_loc (prod_id, loc_id, min_qty, max_qty)
SELECT p.prod_id, l.loc_id, 20, 200 FROM prod p, loc l
WHERE p.prod_nm = '왕교자 만두 1kg' AND l.loc_cd = 'PIK-FRZ-01-01' ON CONFLICT (loc_id) DO NOTHING;

-- 상품 거래처 마스터 (상품×벤더). 자동발주의 기준값 — 발주점·상한은 낱개(EA), 최소주문수량은 입고단위.
-- 왕교자 만두는 발주점을 창고 순재고보다 높게 잡아 첫 산정에서 제안이 나오게 했다 (검증용).
-- 신라면은 입고단위가 파렛트(480 EA)라 MOQ 1이어도 한 번에 크게 들어온다 — 올림 환산 확인용.
INSERT INTO prod_vndr (prod_id, vendor_id, min_qty, max_qty, min_odr_qty, lead_days, prty)
SELECT p.prod_id, v.vendor_id, 400, 2400, 1, 3, 1 FROM prod p, vendor v
WHERE p.prod_nm = '신라면 멀티팩 (5입)' AND v.vndr_cd = 'VD-0001' ON CONFLICT (prod_id, vendor_id) DO NOTHING;
INSERT INTO prod_vndr (prod_id, vendor_id, min_qty, max_qty, min_odr_qty, lead_days, prty)
SELECT p.prod_id, v.vendor_id, 200, 600, 10, 1, 1 FROM prod p, vendor v
WHERE p.prod_nm = '서울우유 1L' AND v.vndr_cd = 'VD-0002' ON CONFLICT (prod_id, vendor_id) DO NOTHING;
INSERT INTO prod_vndr (prod_id, vendor_id, min_qty, max_qty, min_odr_qty, lead_days, prty)
SELECT p.prod_id, v.vendor_id, 300, 1000, 5, 2, 1 FROM prod p, vendor v
WHERE p.prod_nm = '왕교자 만두 1kg' AND v.vndr_cd = 'VD-0002' ON CONFLICT (prod_id, vendor_id) DO NOTHING;

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
-- PROD-0001/VD-0001/ST-0001부터 다시 채번돼 uq_prod_cd/uq_vndr_cd/uq_store_cd 유니크 위반이 난다.
INSERT INTO nbr_seq (rule_cd, dync_ky, seq) VALUES
    ('PROD_CD', '-', 21),
    ('VNDR_CD', '-', 6),
    ('STORE_CD', '-', 5)
ON CONFLICT (rule_cd, dync_ky) DO UPDATE SET seq = GREATEST(nbr_seq.seq, EXCLUDED.seq);

-- 점포 (납품 허용 잔여수명 비율: 편의점 > 마트 > 급식 — FEFO 앞단 필터 시나리오용.
--       그룹은 체인 계열, 유형은 업태 — 웨이브 편성·할당 분배 조건 시나리오용)
INSERT INTO store (store_cd, store_nm, store_grp, store_typ, outb_life_rate)
VALUES ('ST-0001', '씨앤유 편의점 강남점', 'CNU', 'CVS', 70) ON CONFLICT (store_cd) DO NOTHING;
INSERT INTO store (store_cd, store_nm, store_grp, store_typ, outb_life_rate)
VALUES ('ST-0002', '씨앤유 편의점 판교점', 'CNU', 'CVS', 70) ON CONFLICT (store_cd) DO NOTHING;
INSERT INTO store (store_cd, store_nm, store_grp, store_typ, outb_life_rate)
VALUES ('ST-0003', '한마음마트 수원점', 'HANMAUM', 'MART', 50) ON CONFLICT (store_cd) DO NOTHING;
INSERT INTO store (store_cd, store_nm, store_grp, store_typ, outb_life_rate)
VALUES ('ST-0004', '한마음마트 일산점', 'HANMAUM', 'MART', 40) ON CONFLICT (store_cd) DO NOTHING;
INSERT INTO store (store_cd, store_nm, store_grp, store_typ, outb_life_rate)
VALUES ('ST-0005', '행복급식센터', 'HAENGBOK', 'FDSVC', 30) ON CONFLICT (store_cd) DO NOTHING;

-- 사용자 — 역할별로 한 명씩. 비밀번호는 전부 wms!1234 (같은 BCrypt 해시를 공유한다 — 개발용).
-- 1234에서 바꾼 이유는 크롬이 유출 목록에 있는 비밀번호를 경고하기 때문이다. 값이 흔한가가
-- 아니라 유출된 적 있는가가 기준이라, 복잡도를 올리는 게 아니라 안 쓰이던 문자열로 바꿔야 한다.
-- 공개 배포본은 이 값을 그대로 두지 말 것 — 조회 계정(viewer)만 남기고 나머지는 화면에서 바꾼다.
-- 아이디는 채번 대상이 아니다(사람이 정한다). manager는 입고+재고 겸직 예시로, 다중 역할이
-- 실제로 도는지 시드만으로 확인할 수 있게 넣었다.
INSERT INTO usr (login_id, usr_nm, pwd) VALUES
    ('admin',    '시스템관리자', '$2a$10$WNropDEgION6AlnCetLkMOCeJpJ0GBuruB9Buo91q9Pc/GwS7wMQG'),
    ('center',   '센터관리자',   '$2a$10$WNropDEgION6AlnCetLkMOCeJpJ0GBuruB9Buo91q9Pc/GwS7wMQG'),
    ('order',    '주문담당',     '$2a$10$WNropDEgION6AlnCetLkMOCeJpJ0GBuruB9Buo91q9Pc/GwS7wMQG'),
    ('inbound',  '입고담당',     '$2a$10$WNropDEgION6AlnCetLkMOCeJpJ0GBuruB9Buo91q9Pc/GwS7wMQG'),
    ('stock',    '재고담당',     '$2a$10$WNropDEgION6AlnCetLkMOCeJpJ0GBuruB9Buo91q9Pc/GwS7wMQG'),
    ('outbound', '출고담당',     '$2a$10$WNropDEgION6AlnCetLkMOCeJpJ0GBuruB9Buo91q9Pc/GwS7wMQG'),
    ('manager',  '입고재고겸직', '$2a$10$WNropDEgION6AlnCetLkMOCeJpJ0GBuruB9Buo91q9Pc/GwS7wMQG'),
    ('viewer',   '조회전용',     '$2a$10$WNropDEgION6AlnCetLkMOCeJpJ0GBuruB9Buo91q9Pc/GwS7wMQG')
ON CONFLICT (login_id) DO NOTHING;

INSERT INTO usr_role (usr_id, role)
SELECT u.usr_id, r.role
FROM usr u
JOIN (VALUES
    ('admin',    'ADMR'),
    ('center',   'CENT_ADMR'),
    ('order',    'ODR_PIC'),
    ('inbound',  'IB_PIC'),
    ('stock',    'INV_PIC'),
    ('outbound', 'OUTB_PIC'),
    ('manager',  'IB_PIC'),
    ('manager',  'INV_PIC'),
    ('viewer',   'INQ')
) AS r(login_id, role) ON r.login_id = u.login_id
ON CONFLICT (usr_id, role) DO NOTHING;

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

-- =====================================================================
-- 메뉴 카탈로그 · 역할별 메뉴 권한 (2026-08-29 메뉴 권한 설계)
-- 내용은 docs/seed-mnu.sql과 완전히 같다 — 그 파일이 시드의 주인이고
-- 여기(신규 DB 경로)와 docs/migration-add-mnu.sql(기존 DB 경로)이 같은 내용을 쓴다.
-- =====================================================================

INSERT INTO mnu (mnu_cd, mnu_nm, dvsn, grp_nm, srt_seq, icon_nm, scrn_pth, api_prfx, kywd) VALUES
('DASHBOARD', '대시보드', 'WEB', '모니터링', 10, 'LayoutDashboard', '/', NULL, 'dashboard 홈 메인'),
('OMS_IB_ODR', '입고주문', 'WEB', 'OMS', 100, 'FileInput', '/oms/inbound-order', '/oms/inbound-orders', '발주 po purchase order 등록'),
('OMS_IB_ODR_LIST', '입고주문 관리', 'WEB', 'OMS', 110, 'ClipboardList', '/oms/inbound-orders', '/oms/inbound-orders', '발주 목록 확정 취소 삭제'),
('OMS_ATO_ODR', '자동발주 산정', 'WEB', 'OMS', 120, 'Sparkles', '/oms/ato-odr', '/oms/ato-odr', 'ato auto 자동 발주점 순재고 제안 스케줄'),
('OMS_OUTB_ODR', '출고주문', 'WEB', 'OMS', 130, 'FileOutput', '/oms/outbound-order', '/oms/outbound-orders', '수주 so 점포 등록'),
('OMS_OUTB_ODR_LIST', '출고주문 관리', 'WEB', 'OMS', 140, 'FilePlus', '/oms/outbound-orders', '/oms/outbound-orders', '수주 목록 취소'),
('IB_ASN', '입고예정(ASN) 관리', 'WEB', '입고', 200, 'Truck', '/inbound/asn', NULL, 'asn 예정 inbound'),
('IB_RECEIVING', '입고검수', 'WEB', '입고', 210, 'ClipboardCheck', '/inbound/receiving', '/inbound/asns', '검수 수령 receiving lot 제조일자'),
('IB_PTAWY_ODR', '적치지시', 'WEB', '입고', 220, 'ListChecks', '/inbound/putaway-order', '/inbound/putaway', 'putaway 지시 로케이션 배정'),
('IB_PTAWY', '적치', 'WEB', '입고', 230, 'PackageOpen', '/inbound/putaway', '/inbound/putaway', 'putaway 이동 보관'),
('IB_CONFIRM', '입고확정', 'WEB', '입고', 240, 'CheckCircle2', '/inbound/confirm', '/inbound/asns', '확정 confirm 결품 마감'),
('STK_STATUS', '현재고 조회', 'WEB', '재고', 300, 'Box', '/stock/status', NULL, 'inventory 재고 현황 수량 map 맵 점유 로케이션 평면도 구조도 랙 베이 레벨 빈자리 occupancy'),
('STK_HIST', '재고 이력 조회', 'WEB', '재고', 310, 'History', '/stock/history', NULL, 'inventory history 원장 입출고'),
('STK_ATTR', '재고 속성변경', 'WEB', '재고', 320, 'Tags', '/stock/attribute', '/inventory/lot-attrs', 'lot 유통기한 제조일자 정정 변경 전량 라벨 유지'),
('STK_LOT_CHNG', '재고 로트변경', 'WEB', '재고', 330, 'Split', '/stock/lot-change', '/inventory/lot-chngs', 'lot 로트 분할 병합 부분 수량 정정 split merge'),
('STK_HOLD', '재고 보류', 'WEB', '재고', 340, 'PauseCircle', '/stock/hold', '/inventory/holds', 'hold 출고 금지'),
('STK_MOVE', '재고 이동', 'WEB', '재고', 350, 'ArrowLeftRight', '/stock/move', '/inventory/moves', 'move 로케이션 이동 지시 예약 등록 확정 취소'),
('MST_FXNG_LOC', '고정 로케이션 관리', 'WEB', '재고', 360, 'Pin', '/master/fxng-loc', '/master/fxng-locs', 'fxng fixed 고정 피킹존 보충 재보충점 마스터'),
('STK_SPMT', '정기 보충', 'WEB', '재고', 370, 'Repeat', '/stock/spmt', '/inventory/spmt', '보충 replenish spmt min max 피킹존 고정로케이션 fefo 재보충점'),
('STK_STKTK', '재고조사', 'WEB', '재고', 380, 'Calculator', '/stock/count', '/inventory/stocktakes', '실사 count 차이 오차 전산수량 블라인드'),
('STK_ADJ', '재고조정', 'WEB', '재고', 390, 'SlidersHorizontal', '/stock/adjust', '/inventory/adjs', 'adjust 조정 폐기 스크랩 불량 반품 견본 처분 증감 scrap'),
('OUTB_ODR', '출고예정 관리', 'WEB', '출고', 400, 'PackageCheck', '/outbound/order', NULL, '출고예정 출고주문 obs outbound order 예정 창고 문서 조회'),
('OUTB_WAV', '웨이브 편성', 'WEB', '출고', 410, 'Layers', '/outbound/wave', '/outbound/waves', 'wave 묶음 출고주문 담기 전략 실행 피킹지시 발행단위'),
('OUTB_ALOC', '할당', 'WEB', '출고', 420, 'Shuffle', '/outbound/allocation', '/outbound/allocations', 'allocation 재고 배정 fefo'),
('OUTB_PIKNG_ODR', '피킹지시', 'WEB', '출고', 430, 'ScrollText', '/outbound/pick-order', '/outbound/picking-tasks', 'picking 지시'),
('OUTB_RPLN', '수시보충', 'WEB', '출고', 440, 'PackagePlus', '/outbound/replenishment', '/outbound/replenishment', 'replenishment 보충 피킹존 보관존 이동'),
('OUTB_PIKNG', '피킹', 'WEB', '출고', 450, 'PackageOpen', '/outbound/picking', '/outbound/picking', 'picking 집품'),
('OUTB_SHMT', '출고확정', 'WEB', '출고', 460, 'Send', '/outbound/shipping', '/outbound/shipping', 'shipping 상차 출하'),
('MST_ZON', '존 관리', 'WEB', '창고', 500, 'LayoutGrid', '/master/zone', '/master/zons', 'zone 존 보관유형'),
('MST_LOC', '로케이션 관리', 'WEB', '창고', 510, 'MapPin', '/master/location', '/master/locs', 'location 로케이션 랙'),
('MST_PROD', '상품 관리', 'WEB', '마스터', 600, 'Barcode', '/master/prod', '/master/prods', 'product 상품 기준정보 온도대'),
('MST_UOM', '단위 관리', 'WEB', '마스터', 610, 'Ruler', '/master/uom', '/master/prod-uoms', 'uom 포장 낱개수량 중량 박스 파렛트'),
('MST_PROD_VNDR', '상품 거래처 관리', 'WEB', '마스터', 620, 'Handshake', '/master/prod-vndr', '/master/prod-vndrs', 'prod vendor 공급 발주점 발주상한 자동발주 moq 최소주문 리드타임'),
('MST_VNDR', '벤더 관리', 'WEB', '마스터', 630, 'Truck', '/master/vendor', '/master/vendors', 'vendor 거래처 납품처'),
('MST_STORE', '점포 관리', 'WEB', '마스터', 640, 'Store', '/master/store', '/master/stores', 'store 점포 매장'),
('MST_NBR_RULE', '채번규칙 관리', 'WEB', '마스터', 650, 'Hash', '/master/nbr-rules', '/master/nbr-rules', 'nbr 채번 번호 규칙 패턴 시퀀스'),
('MST_CODE', '공통코드 관리', 'WEB', '마스터', 660, 'ListTree', '/master/codes', '/master/codes', 'code 공통코드 그룹 코드값 온도대 보관유형 업무구분 발주구분 계량단위'),
('MST_LABEL', '라벨 인쇄', 'WEB', '마스터', 670, 'Printer', '/master/labels', NULL, 'label 라벨 barcode 바코드 code128 인쇄 print 출력 로케이션 상품 lot pda 스캔'),
('MST_USR', '사용자 관리', 'WEB', '마스터', 680, 'Users', '/master/usr', '/master/usrs', 'user 사용자 계정 로그인 역할 role 권한 비밀번호'),
('MNU_MST', '메뉴 관리', 'WEB', '마스터', 685, 'List', '/master/menu', '/master/mnus', 'menu 메뉴 등록 순서'),
('MNU_AUTH', '권한별 메뉴 관리', 'WEB', '마스터', 690, 'ShieldCheck', '/master/menu-auth', '/master/mnus/roles', 'auth 권한 역할 메뉴'),
('STGY_INSP', '검수 정책관리', 'WEB', '전략', 700, 'ShieldCheck', '/strategy/inspection', '/strategy/inspection-policy', 'inspection 검수 제약 정책 역순제한 유통기한 잔여비율 전략 입고'),
('STGY_PTAWY', '적치 전략관리', 'WEB', '전략', 710, 'Settings2', '/strategy/putaway', '/strategy/putaway-strategies', 'putaway strategy 전략 추천 단계 로케이션 입고'),
('STGY_WAV', '웨이브 전략관리', 'WEB', '전략', 720, 'Waves', '/strategy/wave', '/strategy/wave-strategies', 'wave strategy 웨이브 편성 출고 조건그룹 출고유형 차량편수 전략'),
('STGY_ALOC', '할당 전략관리', 'WEB', '전략', 730, 'Shuffle', '/strategy/allocation', '/strategy/allocation-strategies', 'allocation strategy 할당 분배 재고 배정 fefo 전략 출고'),
('PDA_ENTRY', '현장 작업', 'WEB', 'PDA', 800, 'Smartphone', '/m', NULL, 'pda 모바일 mobile 스캐너 barcode rf 현장 실행 피킹 적치 재고이동 재고조사'),
('PDA_RECEIVING', '입고검수', 'PDA', '입고', 810, 'ClipboardCheck', '/m/receiving', '/inbound/asns', '검수 수령 receiving 스캔 제조일자'),
('PDA_PTAWY', '적치', 'PDA', '입고', 815, 'Layers', '/m/putaway', '/inbound/putaway', 'putaway 이동 보관 스캔'),
('PDA_STK_INQ', '현재고 조회', 'PDA', '재고', 820, 'Search', '/m/stock-inquiry', NULL, 'inventory 재고 조회 스캔'),
('PDA_STK_MOVE', '재고이동', 'PDA', '재고', 824, 'ArrowLeftRight', '/m/stock-move', '/inventory/moves', 'move 이동 확정 스캔'),
('PDA_STKTK', '재고조사', 'PDA', '재고', 828, 'Calculator', '/m/stock-count', '/inventory/stocktakes', '실사 count 블라인드 스캔'),
('PDA_RPLN', '보충', 'PDA', '출고', 830, 'PackagePlus', '/m/replenishment', '/outbound/replenishment', 'replenishment 보충 확정 스캔'),
('PDA_PIKNG', '피킹', 'PDA', '출고', 834, 'PackageOpen', '/m/picking', '/outbound/picking', 'pikng 집품'),
('PDA_SHMT', '출고확정', 'PDA', '출고', 838, 'Send', '/m/shipping', '/outbound/shipping', 'shipping 상차 확정 스캔');

INSERT INTO mnu_role (mnu_cd, role) VALUES
('DASHBOARD', 'CENT_ADMR'),
('DASHBOARD', 'ODR_PIC'),
('DASHBOARD', 'IB_PIC'),
('DASHBOARD', 'INV_PIC'),
('DASHBOARD', 'OUTB_PIC'),
('DASHBOARD', 'INQ'),
('OMS_IB_ODR', 'ODR_PIC'),
('OMS_IB_ODR', 'INQ'),
('OMS_IB_ODR_LIST', 'ODR_PIC'),
('OMS_IB_ODR_LIST', 'INQ'),
('OMS_ATO_ODR', 'ODR_PIC'),
('OMS_ATO_ODR', 'INQ'),
('OMS_OUTB_ODR', 'ODR_PIC'),
('OMS_OUTB_ODR', 'INQ'),
('OMS_OUTB_ODR_LIST', 'ODR_PIC'),
('OMS_OUTB_ODR_LIST', 'INQ'),
('IB_ASN', 'CENT_ADMR'),
('IB_ASN', 'IB_PIC'),
('IB_ASN', 'INQ'),
('IB_RECEIVING', 'CENT_ADMR'),
('IB_RECEIVING', 'IB_PIC'),
('IB_RECEIVING', 'INQ'),
('IB_PTAWY_ODR', 'CENT_ADMR'),
('IB_PTAWY_ODR', 'IB_PIC'),
('IB_PTAWY_ODR', 'INQ'),
('IB_PTAWY', 'CENT_ADMR'),
('IB_PTAWY', 'IB_PIC'),
('IB_PTAWY', 'INQ'),
('IB_CONFIRM', 'CENT_ADMR'),
('IB_CONFIRM', 'IB_PIC'),
('IB_CONFIRM', 'INQ'),
('STK_STATUS', 'CENT_ADMR'),
('STK_STATUS', 'INV_PIC'),
('STK_STATUS', 'INQ'),
('STK_HIST', 'CENT_ADMR'),
('STK_HIST', 'INV_PIC'),
('STK_HIST', 'INQ'),
('STK_ATTR', 'CENT_ADMR'),
('STK_ATTR', 'INV_PIC'),
('STK_ATTR', 'INQ'),
('STK_LOT_CHNG', 'CENT_ADMR'),
('STK_LOT_CHNG', 'INV_PIC'),
('STK_LOT_CHNG', 'INQ'),
('STK_HOLD', 'CENT_ADMR'),
('STK_HOLD', 'INV_PIC'),
('STK_HOLD', 'INQ'),
('STK_MOVE', 'CENT_ADMR'),
('STK_MOVE', 'INV_PIC'),
('STK_MOVE', 'INQ'),
('MST_FXNG_LOC', 'CENT_ADMR'),
('MST_FXNG_LOC', 'INV_PIC'),
('MST_FXNG_LOC', 'INQ'),
('STK_SPMT', 'CENT_ADMR'),
('STK_SPMT', 'INV_PIC'),
('STK_SPMT', 'INQ'),
('STK_STKTK', 'CENT_ADMR'),
('STK_STKTK', 'INV_PIC'),
('STK_STKTK', 'INQ'),
('STK_ADJ', 'CENT_ADMR'),
('STK_ADJ', 'INV_PIC'),
('STK_ADJ', 'INQ'),
('OUTB_ODR', 'CENT_ADMR'),
('OUTB_ODR', 'OUTB_PIC'),
('OUTB_ODR', 'INQ'),
('OUTB_WAV', 'CENT_ADMR'),
('OUTB_WAV', 'OUTB_PIC'),
('OUTB_WAV', 'INQ'),
('OUTB_ALOC', 'CENT_ADMR'),
('OUTB_ALOC', 'OUTB_PIC'),
('OUTB_ALOC', 'INQ'),
('OUTB_PIKNG_ODR', 'CENT_ADMR'),
('OUTB_PIKNG_ODR', 'OUTB_PIC'),
('OUTB_PIKNG_ODR', 'INQ'),
('OUTB_RPLN', 'CENT_ADMR'),
('OUTB_RPLN', 'OUTB_PIC'),
('OUTB_RPLN', 'INQ'),
('OUTB_PIKNG', 'CENT_ADMR'),
('OUTB_PIKNG', 'OUTB_PIC'),
('OUTB_PIKNG', 'INQ'),
('OUTB_SHMT', 'CENT_ADMR'),
('OUTB_SHMT', 'OUTB_PIC'),
('OUTB_SHMT', 'INQ'),
('MST_ZON', 'CENT_ADMR'),
('MST_ZON', 'INQ'),
('MST_LOC', 'CENT_ADMR'),
('MST_LOC', 'INQ'),
('MST_PROD', 'INQ'),
('MST_UOM', 'INQ'),
('MST_PROD_VNDR', 'INQ'),
('MST_VNDR', 'INQ'),
('MST_STORE', 'INQ'),
('MST_NBR_RULE', 'INQ'),
('MST_CODE', 'INQ'),
('MST_LABEL', 'INQ'),
('STGY_INSP', 'CENT_ADMR'),
('STGY_INSP', 'INQ'),
('STGY_PTAWY', 'CENT_ADMR'),
('STGY_PTAWY', 'INQ'),
('STGY_WAV', 'CENT_ADMR'),
('STGY_WAV', 'INQ'),
('STGY_ALOC', 'CENT_ADMR'),
('STGY_ALOC', 'INQ'),
('PDA_ENTRY', 'CENT_ADMR'),
('PDA_ENTRY', 'ODR_PIC'),
('PDA_ENTRY', 'IB_PIC'),
('PDA_ENTRY', 'INV_PIC'),
('PDA_ENTRY', 'OUTB_PIC'),
('PDA_ENTRY', 'INQ'),
('PDA_RECEIVING', 'CENT_ADMR'),
('PDA_RECEIVING', 'IB_PIC'),
('PDA_PTAWY', 'CENT_ADMR'),
('PDA_PTAWY', 'IB_PIC'),
('PDA_STK_INQ', 'CENT_ADMR'),
('PDA_STK_INQ', 'IB_PIC'),
('PDA_STK_INQ', 'INV_PIC'),
('PDA_STK_INQ', 'OUTB_PIC'),
('PDA_STK_MOVE', 'CENT_ADMR'),
('PDA_STK_MOVE', 'INV_PIC'),
('PDA_STKTK', 'CENT_ADMR'),
('PDA_STKTK', 'INV_PIC'),
('PDA_RPLN', 'CENT_ADMR'),
('PDA_RPLN', 'OUTB_PIC'),
('PDA_PIKNG', 'CENT_ADMR'),
('PDA_PIKNG', 'OUTB_PIC'),
('PDA_SHMT', 'CENT_ADMR'),
('PDA_SHMT', 'OUTB_PIC');
-- MNU_MST · MNU_AUTH는 mnu_role 행이 하나도 없다 — 관리자 전용이고 ADMR은 매핑 대상이 아니다
