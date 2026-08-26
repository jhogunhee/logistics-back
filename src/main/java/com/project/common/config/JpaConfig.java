package com.project.common.config;

import com.project.common.security.AuthUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class JpaConfig {

    /**
     * created_by/updated_by 작성자. 로그인한 사용자의 아이디를 쓰고, 인증 없이 도는 실행
     * (정기보충·자동발주 스케줄러)은 'system'으로 남긴다.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(AuthUser.current().map(AuthUser::loginId).orElse("system"));
    }
}
