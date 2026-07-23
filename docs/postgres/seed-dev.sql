-- 개발용 SKU 샘플 데이터 (식품/음료 유통센터 컨셉) — PostgreSQL / Supabase
--   원본: docs/seed-dev.sql (Oracle PL/SQL). 문법만 변환한 파생 파일.
--   변환 메모:
--    - LPAD(seq.NEXTVAL, n, '0') → lpad(nextval('seq')::text, n, '0')  (nextval은 bigint 반환 → text 캐스팅 필요)
--    - Oracle DECLARE/BEGIN/END + RETURNING INTO 익명 블록은 PostgreSQL에 없는 문법이라
--      WITH ... AS (INSERT ... RETURNING ...) CTE 패턴으로 재작성 (Supabase SQL 에디터에서 그대로 실행 가능)
-- sku_cd는 백엔드 채번과 충돌하지 않도록 반드시 sku_cd_seq로 발급한다.
-- shelf_life_days NULL = 유통기한 미관리 (공산품, 유통기한 표시 면제 품목 등)
-- 실행: Supabase SQL Editor 또는 `psql ... -f seed-dev.sql` (UTF-8)

-- 상온(DRY)
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '제주 삼다수 2L', 'DRY', 365);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '신라면 멀티팩 (5입)', 'DRY', 180);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '햇반 백미 210g', 'DRY', 270);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '일회용 종이컵 1000입', 'DRY', NULL);

-- 냉장(CHL)
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '서울우유 1L', 'CHL', 14);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '딸기 요거트 4입', 'CHL', 21);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '참치마요 삼각김밥', 'CHL', 2);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '국산콩 두부 300g', 'CHL', 14);

-- 냉동(FRZ)
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '왕교자 만두 1kg', 'FRZ', 365);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '냉동 새우살 500g', 'FRZ', 540);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '붕어싸만코 (아이스크림)', 'FRZ', NULL);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '코카콜라 350ml (24입)', 'DRY', 365);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '진라면 순한맛 멀티팩 (5입)', 'DRY', 240);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '백설 밀가루 1kg', 'DRY', 540);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '스팸 클래식 200g', 'DRY', 1095);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '물티슈 캡형 100매', 'DRY', NULL);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '바나나우유 240ml', 'CHL', 12);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '슬라이스 치즈 20매', 'CHL', 60);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '닭가슴살 샐러드', 'CHL', 3);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '모짜렐라 피자치즈 1kg', 'FRZ', 365);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || lpad(nextval('sku_cd_seq')::text, 4, '0'), '냉동 블루베리 1kg', 'FRZ', 720);

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

-- 점포 (납품 허용 잔여수명 비율: 편의점 > 마트 > 급식 — FEFO 앞단 필터 시나리오용)
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0001', '씨앤유 편의점 강남점', 70);
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0002', '씨앤유 편의점 판교점', 70);
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0003', '한마음마트 수원점', 50);
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0004', '한마음마트 일산점', 40);
INSERT INTO store (store_cd, store_nm, outb_life_rate) VALUES ('ST-0005', '행복급식센터', 30);

COMMIT;

-- 입고예정(ASN). 전부 SCHEDULED — 검수/마감은 화면에서 진행해야 재고 불변식(이력 합계=스냅샷)이 지켜진다.
-- Oracle의 DECLARE/BEGIN...RETURNING INTO 익명 블록 대신, 헤더 INSERT를 CTE로 두고
-- 그 RETURNING 결과에 라인 INSERT를 SELECT로 이어붙이는 방식으로 헤더+라인을 한 문장에서 처리한다.
WITH new_order AS (
    INSERT INTO ib_order (ib_no, status, vndr_nm, expct_dt)
        VALUES ('IB-20260717-' || lpad(nextval('ib_no_seq')::text, 3, '0'), 'SCHEDULED', '서울식품', DATE '2026-07-17')
        RETURNING ib_order_id
)
INSERT INTO ib_line (ib_order_id, sku_id, expct_qty)
SELECT new_order.ib_order_id, sku.sku_id, v.expct_qty
FROM new_order
CROSS JOIN (VALUES
    ('서울우유 1L', 50),
    ('딸기 요거트 4입', 40),
    ('참치마요 삼각김밥', 30)
) AS v(sku_nm, expct_qty)
JOIN sku ON sku.sku_nm = v.sku_nm;

WITH new_order AS (
    INSERT INTO ib_order (ib_no, status, vndr_nm, expct_dt)
        VALUES ('IB-20260717-' || lpad(nextval('ib_no_seq')::text, 3, '0'), 'SCHEDULED', '콜드체인프레시', DATE '2026-07-17')
        RETURNING ib_order_id
)
INSERT INTO ib_line (ib_order_id, sku_id, expct_qty)
SELECT new_order.ib_order_id, sku.sku_id, v.expct_qty
FROM new_order
CROSS JOIN (VALUES
    ('왕교자 만두 1kg', 80),
    ('냉동 새우살 500g', 60),
    ('붕어싸만코 (아이스크림)', 120)
) AS v(sku_nm, expct_qty)
JOIN sku ON sku.sku_nm = v.sku_nm;

WITH new_order AS (
    INSERT INTO ib_order (ib_no, status, vndr_nm, expct_dt)
        VALUES ('IB-20260718-' || lpad(nextval('ib_no_seq')::text, 3, '0'), 'SCHEDULED', '대한물류', DATE '2026-07-18')
        RETURNING ib_order_id
)
INSERT INTO ib_line (ib_order_id, sku_id, expct_qty)
SELECT new_order.ib_order_id, sku.sku_id, v.expct_qty
FROM new_order
CROSS JOIN (VALUES
    ('제주 삼다수 2L', 300),
    ('햇반 백미 210g', 200),
    ('일회용 종이컵 1000입', 100)
) AS v(sku_nm, expct_qty)
JOIN sku ON sku.sku_nm = v.sku_nm;

WITH new_order AS (
    INSERT INTO ib_order (ib_no, status, vndr_nm, expct_dt)
        VALUES ('IB-20260719-' || lpad(nextval('ib_no_seq')::text, 3, '0'), 'SCHEDULED', '한마음유통', DATE '2026-07-19')
        RETURNING ib_order_id
)
INSERT INTO ib_line (ib_order_id, sku_id, expct_qty)
SELECT new_order.ib_order_id, sku.sku_id, v.expct_qty
FROM new_order
CROSS JOIN (VALUES
    ('스팸 클래식 200g', 150),
    ('바나나우유 240ml', 60)
) AS v(sku_nm, expct_qty)
JOIN sku ON sku.sku_nm = v.sku_nm;

COMMIT;
