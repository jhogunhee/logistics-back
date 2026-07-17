package com.project.wmsback.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class JpaConfig {

    /** created_by/updated_by 작성자. 인증 도입 전까지 'admin' 고정, 로그인 붙이면 SecurityContext에서 꺼내도록 교체 */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of("admin");
    }
}
