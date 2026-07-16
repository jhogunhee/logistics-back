-- 개발용 SKU 샘플 데이터 (식품/음료 유통센터 컨셉)
-- sku_cd는 백엔드 채번과 충돌하지 않도록 반드시 sku_cd_seq로 발급한다.
-- shelf_life_days NULL = 유통기한 미관리 (공산품, 유통기한 표시 면제 품목 등)
-- 실행: NLS_LANG=KOREAN_KOREA.AL32UTF8 설정 후 sqlplus에서 @ 로 실행 (UTF-8, BOM 없음)

-- 상온(DRY)
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '제주 삼다수 2L', 'DRY', 365);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '신라면 멀티팩 (5입)', 'DRY', 180);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '햇반 백미 210g', 'DRY', 270);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '일회용 종이컵 1000입', 'DRY', NULL);

-- 냉장(CHL)
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '서울우유 1L', 'CHL', 14);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '딸기 요거트 4입', 'CHL', 21);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '참치마요 삼각김밥', 'CHL', 2);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '국산콩 두부 300g', 'CHL', 14);

-- 냉동(FRZ)
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '왕교자 만두 1kg', 'FRZ', 365);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '냉동 새우살 500g', 'FRZ', 540);
INSERT INTO sku (sku_cd, sku_nm, temp_zone, shelf_life_days)
    VALUES ('SKU-' || LPAD(sku_cd_seq.NEXTVAL, 4, '0'), '붕어싸만코 (아이스크림)', 'FRZ', NULL);

COMMIT;
EXIT
