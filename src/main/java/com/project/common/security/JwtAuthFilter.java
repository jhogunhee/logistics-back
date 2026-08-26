package com.project.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * {@code Authorization: Bearer …} 를 읽어 SecurityContext를 채운다.
 *
 * <p><b>스프링 빈이 아니다.</b> {@code Filter} 타입 빈은 서블릿 컨테이너 필터 체인에도 자동
 * 등록되는데, 그러면 시큐리티 체인보다 먼저 돌아 뒤따르는 {@code SecurityContextHolderFilter}가
 * 우리가 넣은 인증을 (STATELESS라 비어 있는) 저장소 컨텍스트로 덮어쓴다.
 * {@code SecurityConfig}가 직접 만들어 체인 안에만 끼운다.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            // 토큰이 없거나 깨졌으면 컨텍스트를 비운 채 통과시킨다 — 거부는 인가 단계의 일이다
            tokenProvider.parse(header.substring(PREFIX.length()))
                    .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(authentication(user)));
        }
        chain.doFilter(request, response);
    }

    private static Authentication authentication(AuthUser user) {
        List<SimpleGrantedAuthority> authorities = user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(user, null, authorities);
    }
}
