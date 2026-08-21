-- =====================================================================
-- 락 대기 상한 — 역할 단위 lock_timeout.
--
-- Supabase 기본은 lock_timeout = 0(무한 대기), statement_timeout = 2min(postgres 역할).
-- 락 대기도 statement_timeout 에 걸려 2분에서 끊기긴 하지만, 사용자에게는 원인 없는 오류로
-- 보이고 2분은 화면 앞에서 기다리기엔 길다. 웨이브 생성 전략 실행처럼 락을 오래 쥐는 경로가
-- 있으면 그동안 할당·발행·피킹이 전부 2분씩 매달렸다 실패한다.
--
-- 세션 SET(Hikari connection-init-sql)이 아니라 역할에 거는 이유: transaction 풀링으로
-- 바뀌면 세션 설정은 백엔드마다 흩어져 걸린 듯 안 걸린다. 지금은 직결(session 모드)이지만 접속 방식에
-- 기대지 않는 자리에 둔다 — statement_timeout 을 Supabase 가 걸어 둔 것과 같은 자리다.
-- statement_timeout 은 건드리지 않는다(2min 상한 유지).
--
-- 적용 후 새 커넥션부터 유효 — 백엔드를 재시작할 것. 확인: SHOW lock_timeout;
-- =====================================================================

ALTER ROLE postgres SET lock_timeout = '10s';
