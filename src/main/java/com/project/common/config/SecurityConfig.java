package com.project.common.config;

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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;

/**
 * URL 접두 하나로 역할을 가른다 — 컨트롤러에 {@code @PreAuthorize}를 흩뿌리지 않는다.
 * 접두가 곧 업무 구역이라 규칙이 한 곳에 모이고, 새 컨트롤러는 접두만 지키면 자동으로 규칙 안에 든다.
 *
 * <p>인증은 <b>세션</b>이다. 토큰에 역할을 구워 넣으면 관리자가 권한을 바꿔도 이미 로그인한
 * 사람은 만료까지 옛 권한으로 도는데, 역할·권한을 화면에서 편집할 수 있게 만들 예정이라
 * 그 시차를 둘 수 없다. 세션이면 서버가 그 사람의 세션을 찾아 끊을 수 있다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 세션 쿠키 이름 — application.properties의 server.servlet.session.cookie.name과 같아야 한다 */
    public static final String SESSION_COOKIE = "WMSSESSION";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CorsConfig(WebMvcConfigurer)의 설정을 시큐리티가 읽어 인가보다 먼저 preflight를 끝낸다.
            // 이게 없으면 Authorization/쿠키 없는 OPTIONS가 인가 단계에서 막혀 모든 비GET이 CORS 오류가 된다
            .cors(Customizer.withDefaults())
            // 쿠키 인증이라 CSRF를 켠다 — SameSite=None이면 다른 사이트의 요청에도 쿠키가 실린다.
            // 토큰은 세션에 두고 응답 본문으로 내보낸다(쿠키 방식은 프론트가 다른 도메인이라 읽지 못한다).
            // 로그인만 예외 — 세션이 아직 없어 받을 토큰도 없다
            .csrf(csrf -> csrf.ignoringRequestMatchers("/auth/login", "/auth/scan-login"))
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation().newSession())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((rq, rs, e) -> write(rs, HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다."))
                    .accessDeniedHandler((rq, rs, e) -> write(rs, HttpServletResponse.SC_FORBIDDEN, "권한이 없습니다.")))
            .logout(logout -> logout
                    .logoutUrl("/auth/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies(SESSION_COOKIE)
                    .logoutSuccessHandler((rq, rs, auth) -> rs.setStatus(HttpServletResponse.SC_NO_CONTENT)))
            .authorizeHttpRequests(auth -> auth
                    // 컨트롤러 밖에서 터진 예외의 /error forward까지 denyAll에 걸리면 진짜 원인이 403으로 덮인다
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                    // PDA 간편 로그인 — 로그인과 같은 자리다(세션도 토큰도 이게 만들어 준다).
                    // 아이디만으로 세션이 열리는 문이라 누구에게 열지는 AuthService가 역할로 가른다
                    .requestMatchers(HttpMethod.POST, "/auth/scan-login").permitAll()
                    // /health는 로그인 전에 불린다 — 프론트의 기동 대기 게이트와 슬립 방지 크론이 쓴다.
                    // 메서드를 걸지 않는 이유는 크론의 HEAD가 GET 매처에 안 걸려 denyAll까지 흘러서다
                    .requestMatchers("/health").permitAll()
                    // 사용자 목록은 조회도 관리자만이라 GET 규칙보다 앞에 둔다
                    .requestMatchers("/master/usrs/**").hasRole("ADMR")
                    .requestMatchers(HttpMethod.GET, "/**").authenticated()
                    .requestMatchers("/auth/**").authenticated()
                    // 아래 둘은 /master 접두를 쓰지만 마스터 권한(ADMR)과 성격이 다르다.
                    // 접두가 같아 /master/** 보다 위에 둔다(먼저 걸리는 규칙이 이긴다)
                    //
                    // 고정로케이션은 창고 구조가 아니라 상품×로케이션 재보충 기준(min/max)이다 —
                    // 이 값으로 도는 정기보충(/inventory/spmt)이 INV_PIC이라 짝을 맞춘다
                    .requestMatchers("/master/fxng-locs/**").hasAnyRole("ADMR", "CENT_ADMR", "INV_PIC")
                    // 존 · 로케이션은 창고 물리 구조라 센터 운영 업무다 —
                    // 랙을 늘리는 데 시스템관리자를 부를 이유가 없다
                    .requestMatchers("/master/zons/**", "/master/locs/**").hasAnyRole("ADMR", "CENT_ADMR")
                    .requestMatchers("/master/**").hasRole("ADMR")
                    .requestMatchers("/strategy/**").hasAnyRole("ADMR", "CENT_ADMR")
                    .requestMatchers("/oms/**").hasAnyRole("ADMR", "ODR_PIC")
                    .requestMatchers("/inbound/**").hasAnyRole("ADMR", "CENT_ADMR", "IB_PIC")
                    .requestMatchers("/inventory/**").hasAnyRole("ADMR", "CENT_ADMR", "INV_PIC")
                    .requestMatchers("/outbound/**").hasAnyRole("ADMR", "CENT_ADMR", "OUTB_PIC")
                    .anyRequest().denyAll());
        return http.build();
    }

    /** 로그인이 인증을 세션에 심을 때 쓴다 (필터가 아니라 서비스가 직접 부르는 경로라 빈으로 꺼내 둔다) */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
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
