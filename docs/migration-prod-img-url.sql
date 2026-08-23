-- =====================================================================
-- 증분 마이그레이션: prod.img_url (상품 이미지 주소) 신설
--   대상: migration-spmt.sql 까지 적용된 라이브 DB → docs/schema.sql 상태
--   근거: DB는 주소만 들고 파일은 갖지 않는다 — 그림은 프론트와 함께 배포되는
--         정적 파일(wms-front/public/prod-img/{상품코드}.svg)이다.
--
--   ※ 이 증분을 적용하기 전에 백엔드를 재시작하면 안 된다 — Prod 엔티티가 이 컬럼을
--     매핑하므로 컬럼 없는 DB에서는 상품 조회가 전부 실패한다 (ddl-auto=none).
--
--   재실행 안전: ADD COLUMN IF NOT EXISTS, 백필은 img_url IS NULL 인 행만.
--   BEGIN/COMMIT 없이 DO 블록 하나 (DBeaver 25P02 방지).
-- =====================================================================

DO $mig$
DECLARE
    v_existed BOOLEAN;
    v_filled  int;
BEGIN

    v_existed := EXISTS (SELECT 1 FROM information_schema.columns
                          WHERE table_name = 'prod' AND column_name = 'img_url');

    ALTER TABLE prod ADD COLUMN IF NOT EXISTS img_url VARCHAR(500);
    RAISE NOTICE 'prod.img_url %', CASE WHEN v_existed THEN '이미 존재 — 건너뜀' ELSE '컬럼 추가' END;

    -- 데모 상품 백필 — seed-dev.sql 은 새 DB에만 도는데 라이브 DB에는 이미
    -- PROD-0001~0021 이 들어 있다. 그림 파일이 실재하는 그 범위만 채운다.
    UPDATE prod
       SET img_url = '/prod-img/' || prod_cd || '.svg'
     WHERE img_url IS NULL
       AND prod_cd ~ '^PROD-00(0[1-9]|1[0-9]|2[01])$';
    GET DIAGNOSTICS v_filled = ROW_COUNT;
    RAISE NOTICE '데모 상품 이미지 백필: %건', v_filled;

    -- COMMENT 는 멱등이라 매번 갱신한다 (docs/schema.sql 의 문구와 한 벌로 유지)
    COMMENT ON COLUMN prod.img_url IS '상품 이미지. NULL = 이미지 없음';

END
$mig$;

-- 확인:
--   SELECT prod_cd, img_url FROM prod ORDER BY prod_cd LIMIT 5;
