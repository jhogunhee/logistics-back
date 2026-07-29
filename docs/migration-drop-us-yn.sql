-- =====================================================================
-- 사용여부(us_yn) 컬럼 제거 — vendor · code_detail
--   전제: docs/migration-catchup-to-schema.sql · migration-zon.sql 까지 적용된 DB.
--   migration-uom.sql 과는 서로 독립이라 어느 쪽을 먼저 돌려도 된다.
--
--   배경 —
--   두 테이블이 us_yn 을 갖고 있었다. "지운 것도 아니고 쓰는 것도 아닌" 중간 상태다.
--   이 상태를 두면 값 목록을 읽는 모든 조회에 us_yn='Y' 필터가 따라붙어야 하고,
--   한 군데라도 빠지면 폐기한 값이 콤보박스에 되살아난다. 실제로 화면마다 조회 경로가
--   갈리는 중이었다 — 콤보박스는 사용중만, 관리 그리드는 폐기 포함.
--
--   대신 물리삭제로 운용한다. 하위가 참조 중이면 서비스가 삭제를 거부한다:
--     - code_detail : CodeService.requireUnusedUom() — UOM 그룹이 유일하게 가드를 갖는다
--                     (다른 그룹은 값이 enum 으로 코드에 박혀 있어 저장 단계에서 드러난다)
--     - vendor      : 가드를 두지 않는다. 과거 주문이 참조하던 벤더를 지우면 그 주문의
--                     벤더명이 조회에서 비게 되지만, 사내 운영 데이터라 허용한다.
--
--   ▣ 되돌릴 수 없는 삭제다.
--     지금 us_yn='N' 인 행이 있으면 그 "폐기" 표시가 사라지고 평범한 사용중 행이 된다.
--     아래 1단계가 그런 행을 세어 NOTICE 로 알려주므로, 실제로 지워야 할 행이면
--     스크립트를 돌리기 전에 DELETE 로 정리할 것.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $us_yn$
DECLARE
    n int;
BEGIN
    -- 1. 폐기 표시된 행 확인 -------------------------------------------
    -- 지우기 전에 몇 건이 "사용중"으로 바뀌는지 알려준다. 컬럼이 이미 없으면 건너뛴다.
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'vendor' AND column_name = 'us_yn') THEN
        SELECT count(*) INTO n FROM vendor WHERE us_yn = 'N';
        IF n > 0 THEN
            RAISE NOTICE '주의 — vendor 에 us_yn=N 인 행이 % 건 있다. 삭제 후에는 사용중 벤더가 된다', n;
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'code_detail' AND column_name = 'us_yn') THEN
        SELECT count(*) INTO n FROM code_detail WHERE us_yn = 'N';
        IF n > 0 THEN
            RAISE NOTICE '주의 — code_detail 에 us_yn=N 인 코드가 % 건 있다. 삭제 후에는 콤보박스에 다시 나온다', n;
        END IF;
    END IF;

    -- 2. vendor.us_yn 제거 ---------------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'vendor' AND column_name = 'us_yn') THEN
        ALTER TABLE vendor DROP COLUMN us_yn;   -- ck_vendor_us_yn 제약도 함께 사라진다
        RAISE NOTICE 'vendor.us_yn 삭제';
    ELSE
        RAISE NOTICE 'vendor.us_yn 이미 없음 — 건너뜀';
    END IF;

    COMMENT ON TABLE vendor IS '벤더(납품처) 마스터. 입고주문·입고예정의 거래처. 거래 종료는 물리삭제';

    -- 3. code_detail.us_yn 제거 ----------------------------------------
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'code_detail' AND column_name = 'us_yn') THEN
        ALTER TABLE code_detail DROP COLUMN us_yn;   -- ck_code_us_yn 제약도 함께 사라진다
        RAISE NOTICE 'code_detail.us_yn 삭제';
    ELSE
        RAISE NOTICE 'code_detail.us_yn 이미 없음 — 건너뜀';
    END IF;

    COMMENT ON COLUMN code_detail.code_cd IS '코드 값 (예: DRY). 로직에서 리터럴로 참조하므로 변경 금지';

    -- 4. 검증 ---------------------------------------------------------
    SELECT count(*) INTO n FROM information_schema.columns WHERE column_name = 'us_yn';
    IF n > 0 THEN
        RAISE EXCEPTION 'us_yn 컬럼이 아직 % 건 남아 있다. 이 스크립트가 모르는 테이블이 생긴 것이니 '
                        '확인할 것: SELECT table_name FROM information_schema.columns '
                        'WHERE column_name = ''us_yn'';', n;
    END IF;

    RAISE NOTICE '사용여부 컬럼 제거 완료 — 전 테이블 us_yn 0건';
END
$us_yn$;

-- =====================================================================
-- 적용 후 확인
--   1) 전 테이블 us_yn 0건 (위 4단계가 이미 막지만 재확인용)
--      SELECT table_name FROM information_schema.columns WHERE column_name = 'us_yn';
--   2) CHECK 제약도 함께 사라졌는지
--      SELECT conname FROM pg_constraint WHERE conname IN ('ck_vendor_us_yn', 'ck_code_us_yn');
-- =====================================================================
