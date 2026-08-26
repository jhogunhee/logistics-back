package com.project.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

/**
 * 인증 주체. 토큰 claim을 그대로 담는다.
 *
 * <p>DB를 다시 보지 않으려고 이 형태다 — {@code common}이 {@code mdm}을 import하지 않아야 하므로
 * 이 패키지는 {@code Usr} 엔티티를 알 수 없고, 알 필요도 없다.
 */
public record AuthUser(String loginId, String usrNm, List<String> roles) {

    /** 인증 없이 도는 실행(스케줄러)에서는 비어 있다 */
    public static Optional<AuthUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
