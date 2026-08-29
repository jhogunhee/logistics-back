package com.project.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 인가 ② 메뉴 권한. ①({@code SecurityConfig}의 업무 구역 상한)을 통과한 요청만 여기 온다 —
 * 둘 다 통과해야 열린다.
 *
 * <p>GET을 보지 않는 이유는 화면 여럿이 같은 조회 API를 쓰기 때문이다. 조회 범위는 상한이 정한다.
 * ADMR을 통과시키는 이유는 잠김 방지다 — 메뉴를 다 끄면 되살릴 사람이 없어진다.
 */
@RequiredArgsConstructor
public class MnuAccessFilter extends OncePerRequestFilter {

    private final MnuAccessSource source;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (blocked(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"이 화면의 권한이 없습니다.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean blocked(HttpServletRequest request) {
        if (HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        return AuthUser.current()
                .filter(user -> !user.roles().contains("ADMR"))
                .map(user -> !source.allows(user.roles(), request.getRequestURI()))
                .orElse(false);
    }
}
