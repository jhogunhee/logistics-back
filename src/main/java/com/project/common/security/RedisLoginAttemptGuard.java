package com.project.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * 실패 횟수를 Redis 카운터로 세는 구현.
 *
 * <p>TTL은 {@code INCR}이 처음 1이 됐을 때만 건다. 매번 걸면 「마지막 실패로부터 5분」이 된다.
 *
 * <p>Redis가 죽으면 통과시킨다 — 카운터 때문에 로그인이 막히면 본말전도다.
 */
public class RedisLoginAttemptGuard implements LoginAttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginAttemptGuard.class);

    private static final String ID_KEY = "login:fail:id:";

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration window;

    public RedisLoginAttemptGuard(StringRedisTemplate redis, int maxAttempts, Duration window) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    @Override
    public void assertNotBlocked(String loginId) {
        try {
            blockIfOver(ID_KEY + loginId);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("[LOGIN-GUARD] 차단 조회 실패 — 통과시킨다", e);
        }
    }

    @Override
    public void recordFailure(String loginId) {
        try {
            count(ID_KEY + loginId);
        } catch (RuntimeException e) {
            log.warn("[LOGIN-GUARD] 실패 기록 실패 — 무시한다", e);
        }
    }

    @Override
    public void reset(String loginId) {
        try {
            redis.delete(ID_KEY + loginId);
        } catch (RuntimeException e) {
            log.warn("[LOGIN-GUARD] 카운터 정리 실패 — 무시한다", e);
        }
    }

    private void blockIfOver(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null || Integer.parseInt(value) < maxAttempts) {
            return;
        }
        Long ttl = redis.getExpire(key);
        long minutes = (ttl == null || ttl <= 0) ? 1 : (ttl + 59) / 60;
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "로그인 시도가 너무 많습니다. " + minutes + "분 후에 다시 시도하세요.");
    }

    private void count(String key) {
        Long attempts = redis.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redis.expire(key, window);
        }
    }
}
