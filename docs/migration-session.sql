-- =====================================================================
-- 세션 저장소 도입 — spring-session-jdbc의 테이블 2개를 만든다.
--   전제: docs/migration-usr.sql 까지 적용된 DB.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--     전 구간에 존재 확인이 걸려 있어 몇 번을 돌려도 안전하다.
--
--   ▣ 이 두 테이블만 이 프로젝트의 규칙 밖에 있다.
--     · 컬럼 이름이 약어 사전을 따르지 않는다 — 라이브러리가 정한 이름이라 바꾸면 동작하지 않는다.
--     · 아래 FK 하나가 이 스키마의 유일한 FK다. 지우면 안 된다 — 세션 만료 정리가
--       spring_session만 지우고 속성 행은 ON DELETE CASCADE에 기대어 따라 지워진다.
--       (docs/migration-catchup-to-schema.sql의 「FK 전량 제거」 루프를 이 뒤에 다시 돌리지 말 것)
--
--   왜 DB 세션인가: 인메모리 세션은 Render 무료 인스턴스가 유휴 슬립·재시작할 때마다
--   전원 로그아웃된다. 그리고 principal_name 인덱스가 있어야 「역할이 바뀐 사용자의 세션을
--   찾아 끊는다」가 가능하다 — 권한을 화면에서 편집할 수 있게 만들 때 그 시차를 없애는 자리다.
--
--   테이블 생성을 애플리케이션에 맡기지 않는다(spring.session.jdbc.initialize-schema=never) —
--   스키마의 주인은 docs/schema.sql 하나다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
--     - 이 스크립트가 코드보다 먼저 적용돼야 한다. 반대면 로그인 시 세션 저장에 실패한다.
-- =====================================================================

DO $sess$
BEGIN
    IF to_regclass('spring_session') IS NULL THEN
        CREATE TABLE spring_session (
            primary_id            CHAR(36)     NOT NULL,
            session_id            CHAR(36)     NOT NULL,
            creation_time         BIGINT       NOT NULL,
            last_access_time      BIGINT       NOT NULL,
            max_inactive_interval INT          NOT NULL,
            expiry_time           BIGINT       NOT NULL,
            principal_name        VARCHAR(100),
            CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
        );
        CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
        CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
        CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

        COMMENT ON TABLE  spring_session IS '로그인 세션 (spring-session-jdbc 소유)';
        COMMENT ON COLUMN spring_session.principal_name IS '로그인 아이디. 역할이 바뀐 사용자의 세션을 찾아 끊는 축';
        RAISE NOTICE 'spring_session 테이블 생성';
    ELSE
        RAISE NOTICE 'spring_session 테이블 이미 존재 — 건너뜀';
    END IF;

    IF to_regclass('spring_session_attributes') IS NULL THEN
        CREATE TABLE spring_session_attributes (
            session_primary_id CHAR(36)     NOT NULL,
            attribute_name     VARCHAR(200) NOT NULL,
            attribute_bytes    BYTEA        NOT NULL,
            CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
            CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
                REFERENCES spring_session (primary_id) ON DELETE CASCADE
        );
        RAISE NOTICE 'spring_session_attributes 테이블 생성 (이 스키마의 유일한 FK)';
    ELSE
        RAISE NOTICE 'spring_session_attributes 테이블 이미 존재 — 건너뜀';
    END IF;

    RAISE NOTICE '세션 저장소 마이그레이션 완료';
END
$sess$;

-- =====================================================================
-- 적용 후 확인
--   1) 테이블 2개와 인덱스 3개
--      SELECT tablename FROM pg_tables WHERE tablename LIKE 'spring_session%';
--      SELECT indexname FROM pg_indexes WHERE tablename = 'spring_session';
--   2) FK 는 정확히 이것 하나여야 한다 (다른 FK가 늘어난 게 아닌지 확인)
--      SELECT conname, conrelid::regclass FROM pg_constraint WHERE contype = 'f';
--      -- spring_session_attributes_fk 1건
--   3) 로그인 뒤 세션이 실제로 쌓이는지
--      SELECT session_id, principal_name, to_timestamp(expiry_time/1000) AS expires
--        FROM spring_session ORDER BY creation_time DESC;
-- =====================================================================
