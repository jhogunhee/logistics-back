package com.project.common.security;

import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * 인증 주체. 세션에 실려 다닌다.
 *
 * <p>{@code common}이 {@code mdm}을 import하지 않아야 하므로 이 패키지는 {@code Usr} 엔티티를
 * 알 수 없고, 알 필요도 없다 — 로그인 시점에 필요한 값만 복사해 담는다.
 *
 * <p>{@link Serializable}인 이유는 세션 저장소가 DB(spring-session-jdbc)라 직렬화되기 때문이고,
 * {@link AuthenticatedPrincipal}인 이유는 {@code getName()}이 곧
 * {@code spring_session.principal_name}이 되어 <b>「이 사람의 세션을 찾아 끊는다」</b>가
 * 가능해지기 때문이다 (역할이 바뀐 사용자를 즉시 내보내는 자리 — {@code UsrService}).
 */
public record AuthUser(String loginId, String usrNm, List<String> roles)
        implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return loginId;
    }

    /** 인증 없이 도는 실행(스케줄러)에서는 비어 있다 */
    public static Optional<AuthUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
