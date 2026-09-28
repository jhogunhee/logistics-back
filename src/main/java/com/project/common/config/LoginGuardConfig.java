package com.project.common.config;

import com.project.common.security.LoginAttemptGuard;
import com.project.common.security.NoopLoginAttemptGuard;
import com.project.common.security.RedisLoginAttemptGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 로그인 시도 제한 구현 선택. 두 조건이 배타적이라 평가 순서와 무관하게 하나만 등록된다.
 */
@Configuration
public class LoginGuardConfig {

    @Bean
    @ConditionalOnProperty(name = "wms.login-guard.redis.enabled", havingValue = "true")
    public LoginAttemptGuard redisLoginAttemptGuard(
            StringRedisTemplate redisTemplate,
            @Value("${wms.login-guard.max-attempts:5}") int maxAttempts,
            @Value("${wms.login-guard.window:PT5M}") Duration window) {
        return new RedisLoginAttemptGuard(redisTemplate, maxAttempts, window);
    }

    @Bean
    @ConditionalOnProperty(name = "wms.login-guard.redis.enabled", havingValue = "false", matchIfMissing = true)
    public LoginAttemptGuard noopLoginAttemptGuard() {
        return new NoopLoginAttemptGuard();
    }
}
