-- =====================================================================
-- 시드 계정 비밀번호 교체 — 1234 → wms!1234
--   전제: docs/migration-usr.sql 까지 적용된 DB.
--
--   왜 바꾸나: 크롬이 로그인 때 「저장된 비밀번호 확인하기」(유출 비밀번호 경고)를 띄운다.
--   크롬은 입력된 문자열이 <알려진 유출 목록>에 있는지를 조회한다 — 길이나 복잡도가 기준이
--   아니라서 복잡하게 만들어도 유출 목록에 있으면 그대로 뜬다. 안 쓰이던 문자열이어야 한다.
--   그래서 어렵게 만드는 대신 1234 앞에 프로젝트 약어만 붙였다(wms!1234) — 타이핑 부담은
--   거의 그대로면서 유출 목록에는 없다. 최소 길이 8자 정책(Usr.MIN_PWD_LENGTH)에도 딱 맞는다.
--
--   ▣ 전체가 DO 블록 하나다 (BEGIN;/COMMIT; 을 쓰지 않는다) — CLAUDE.md 규칙.
--
--   ▣ 재실행 안전한 이유가 WHERE 절이다 — <아직 옛 해시인 행만> 바꾼다.
--     이미 화면(사용자 관리)에서 비밀번호를 바꿔 둔 계정은 건드리지 않는다.
--     그래서 이 스크립트는 몇 번을 돌려도 안전하고, 운영에서 먼저 손으로 바꿨어도 덮어쓰지 않는다.
--
--   ▣ 지금 배포본은 시연용이라 admin 계정을 그대로 쓴다 (로그인 화면 입력칸에 채워져 있다).
--     wms!1234 는 레포(공개)에 적힌 값이므로 링크를 아는 사람은 관리자로 들어와 저장·삭제까지
--     할 수 있다는 뜻이다 — 시연 중에는 의도한 것이고, 실제로 열게 되면 사용자 관리 화면에서
--       · viewer  → 데모용으로 공개해도 되는 값 (조회 전용이라 저장이 막힌다)
--       · 나머지 7 → 레포에 없는 값
--     으로 바꾸고 로그인 화면의 기본 입력값도 viewer로 되돌리면 된다.
--
--   실행(DBeaver): 이 파일을 열고 Alt+X (Execute script).
--     - NOTICE 는 결과 패널의 Server Output 탭에서 볼 것
-- =====================================================================

DO $pwd$
DECLARE
    n BIGINT;
BEGIN
    IF to_regclass('usr') IS NULL THEN
        RAISE NOTICE 'usr 테이블 없음 — docs/migration-usr.sql 을 먼저 적용할 것';
        RETURN;
    END IF;

    UPDATE usr
       SET pwd = '$2a$10$WNropDEgION6AlnCetLkMOCeJpJ0GBuruB9Buo91q9Pc/GwS7wMQG'   -- wms!1234
     WHERE pwd IN (
        '$2a$10$ndeRZj62lK2QZkSiUQYXl.WwdmHIAXLu8OBmy3D6DMqw2UaWLdBSq',           -- 1234 (최초 시드)
        '$2a$10$Dy109U8CYcTa1TFWLrQOSu/6felLnALTArM3u9xLzCeW.i4S6qse'             -- WareFlow!2026 (중간에 잠깐 쓴 값)
     );

    GET DIAGNOSTICS n = ROW_COUNT;
    IF n = 0 THEN
        RAISE NOTICE '옛 비밀번호(1234)인 계정 없음 — 이미 바뀌었거나 시드가 적용되지 않았다';
    ELSE
        RAISE NOTICE '% 개 계정의 비밀번호를 교체했다', n;
    END IF;
END
$pwd$;

-- =====================================================================
-- 적용 후 확인
--   1) 옛 해시가 0건이어야 한다
--      SELECT count(*) FROM usr
--       WHERE pwd = '$2a$10$ndeRZj62lK2QZkSiUQYXl.WwdmHIAXLu8OBmy3D6DMqw2UaWLdBSq';
--   2) 계정별 현재 해시 앞자리 (같은 값이면 아직 시드 비밀번호를 쓰는 계정이다)
--      SELECT login_id, left(pwd, 20) FROM usr ORDER BY login_id;
--   3) 로그인해서 크롬 경고가 사라졌는지 — 이 작업의 목적이다
-- =====================================================================
