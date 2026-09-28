package com.project.common.security;

/**
 * Redis가 없는 환경에서 들어가는 빈 구현. 세지도 막지도 않는다
 */
public class NoopLoginAttemptGuard implements LoginAttemptGuard {

    @Override
    public void assertNotBlocked(String loginId) {
    }

    @Override
    public void recordFailure(String loginId) {
    }

    @Override
    public void reset(String loginId) {
    }
}
