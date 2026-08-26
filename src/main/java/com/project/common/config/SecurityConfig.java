package com.project.common.config;

import com.project.common.security.JwtAuthFilter;
import com.project.common.security.JwtTokenProvider;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * URL 접두 하나로 역할을 가른다 — 컨트롤러에 {@code @PreAuthorize}를 흩뿌리지 않는다.
 * 접두가 곧 업무 구역이라 규칙이 한 곳에 모이고, 새 컨트롤러는 접두만 지키면 자동으로 규칙 안에 든다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider tokenProvider) throws Exception {
        http
            // CorsConfig(WebMvcConfigurer)의 설정을 시큐리티가 읽어 인가보다 먼저 preflight를 끝낸다.
            // 이게 없으면 Authorization 없는 OPTIONS가 인가 단계에서 막혀 모든 비GET이 CORS 오류가 된다
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((rq, rs, e) -> write(rs, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다."))
                    .accessDeniedHandler((rq, rs, e) -> write(rs, HttpServletResponse.SC_FORBIDDEN, "권한이 없습니다.")))
            .authorizeHttpRequests(auth -> auth
                    // 컨트롤러 밖에서 터진 예외의 /error forward까지 denyAll에 걸리면 진짜 원인이 403으로 덮인다
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                    // /health는 로그인 전에 불린다 — 프론트의 서버 기동 대기 게이트와 슬립 방지 크론이 쓴다
                    .requestMatchers(HttpMethod.GET, "/health").permitAll()
                    // 사용자 목록은 조회도 관리자만이라 GET 규칙보다 앞에 둔다
                    .requestMatchers("/master/usrs/**").hasRole("ADMR")
                    .requestMatchers(HttpMethod.GET, "/**").authenticated()
                    .requestMatchers("/auth/**").authenticated()
                    .requestMatchers("/master/**").hasRole("ADMR")
                    .requestMatchers("/strategy/**").hasAnyRole("ADMR", "CENT_ADMR")
                    .requestMatchers("/oms/**").hasAnyRole("ADMR", "ODR_PIC")
                    .requestMatchers("/inbound/**").hasAnyRole("ADMR", "CENT_ADMR", "IB_PIC")
                    .requestMatchers("/inventory/**").hasAnyRole("ADMR", "CENT_ADMR", "INV_PIC")
                    .requestMatchers("/outbound/**").hasAnyRole("ADMR", "CENT_ADMR", "OUTB_PIC")
                    .anyRequest().denyAll())
            .addFilterBefore(new JwtAuthFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 401·403은 필터 단계라 GlobalExceptionHandler에 닿지 않는다. 본문 형태만 그쪽과 맞춘다 */
    private static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
