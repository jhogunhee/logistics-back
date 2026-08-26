package com.project.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 토큰이 왕복하는지, 그리고 <b>믿으면 안 되는 토큰을 확실히 거절하는지</b>.
 * 검증 실패를 예외가 아니라 빈 값으로 돌려주는 계약도 여기서 고정한다 — 필터가 그걸 전제로
 * 컨텍스트를 비운 채 통과시키고, 401은 인가 단계가 낸다.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "wms-test-secret-key-32-bytes-or-longer!";
    private static final AuthUser USER = new AuthUser("inbound", "입고담당", List.of("IB_PIC", "INV_PIC"));

    @Test
    @DisplayName("발급한 토큰을 다시 읽으면 아이디·이름·역할이 그대로 나온다")
    void issueThenParse() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 12);

        Optional<AuthUser> parsed = provider.parse(provider.issue(USER));

        assertTrue(parsed.isPresent());
        assertEquals("inbound", parsed.get().loginId());
        assertEquals("입고담당", parsed.get().usrNm());
        assertEquals(List.of("IB_PIC", "INV_PIC"), parsed.get().roles());
    }

    @Test
    @DisplayName("만료된 토큰은 빈 값이다")
    void expiredTokenIsRejected() {
        JwtTokenProvider expired = new JwtTokenProvider(SECRET, -1);

        assertTrue(expired.parse(expired.issue(USER)).isEmpty());
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 빈 값이다")
    void forgedSignatureIsRejected() {
        String forged = new JwtTokenProvider("another-secret-key-32-bytes-or-longer!!", 12).issue(USER);

        assertTrue(new JwtTokenProvider(SECRET, 12).parse(forged).isEmpty());
    }

    @Test
    @DisplayName("토큰 형식이 아니면 빈 값이다")
    void garbageIsRejected() {
        assertTrue(new JwtTokenProvider(SECRET, 12).parse("not-a-token").isEmpty());
    }

    @Test
    @DisplayName("서명키가 32바이트 미만이면 기동 시점에 죽는다 — 약한 키로 조용히 돌지 않는다")
    void shortSecretFailsFast() {
        assertThrows(Exception.class, () -> new JwtTokenProvider("too-short", 12));
    }
}
