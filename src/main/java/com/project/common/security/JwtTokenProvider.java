package com.project.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/** JWT 발급·검증. HS256, 리프레시 없음 — 만료되면 재로그인한다. */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USR_NM = "nm";
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey key;
    private final long expiryMillis;

    /** 생성자 주입인 이유: 테스트가 스프링 컨텍스트 없이 {@code new}로 만든다 */
    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiry-hours}") long expiryHours) {
        // 32바이트 미만이면 여기서 던진다 — 약한 키로 조용히 도는 것보다 기동을 막는 편이 낫다
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = Duration.ofHours(expiryHours).toMillis();
    }

    public String issue(AuthUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.loginId())
                .claim(CLAIM_USR_NM, user.usrNm())
                .claim(CLAIM_ROLES, user.roles())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 만료·위조·형식오류를 모두 빈 값으로 돌려준다. 여기서 예외를 던지지 않는 이유는 401을 내는
     * 자리가 필터가 아니라 {@code authenticationEntryPoint}이기 때문이다 — 그래야 토큰이 깨진
     * 요청도 {@code permitAll} 경로(로그인 · /health)에는 그대로 닿는다.
     */
    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthUser(
                    claims.getSubject(),
                    claims.get(CLAIM_USR_NM, String.class),
                    roles(claims)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static List<String> roles(Claims claims) {
        List<?> raw = claims.get(CLAIM_ROLES, List.class);
        return raw == null ? List.of() : raw.stream().map(String::valueOf).toList();
    }
}
