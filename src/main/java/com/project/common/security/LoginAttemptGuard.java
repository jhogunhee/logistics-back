package com.project.common.security;

/**
 * 로그인 실패 횟수를 세어 무차별 대입을 막는 문지기. 카운터는 프로세스 밖에 둔다 —
 * 애플리케이션 메모리에 두면 재시작 때마다 0으로 돌아가 창이 성립하지 않는다.
 */
public interface LoginAttemptGuard {

    /** 차단 중이면 예외를 던진다. 인증을 시도하기 전에 부른다 */
    void assertNotBlocked(String loginId);

    /** 인증 실패 1회 기록 */
    void recordFailure(String loginId);

    /** 인증 성공 — 쌓인 실패를 지운다 */
    void reset(String loginId);
}
