package com.project.common.security;

import java.util.List;
import java.util.Set;

/**
 * 인가 규칙표의 본체. {@code SecurityConfig}가 이 순서대로 체인을 등록하고,
 * 메뉴 권한 화면이 {@link #canWrite}로 같은 표를 읽는다 — 두 벌이 되지 않게 한 곳에 둔다.
 * <p>
 * {@code common}은 {@code mdm}을 모른다(계층 규칙) — 역할은 엔티티가 아니라 세션에 실린
 * 이름 문자열({@code "ADMR"} 등)로 다룬다. {@code AuthUser.roles()}·{@code hasRole}과 같은 형태다.
 */
public final class SecurityRules {

    /** 순서가 곧 규칙이다 — 먼저 걸리는 것이 이긴다 */
    public static final List<Rule> WRITE_RULES = List.of(
            new Rule(List.of("/master/fxng-locs/**"), Set.of("ADMR", "CENT_ADMR", "INV_PIC")),
            new Rule(List.of("/master/zons/**", "/master/locs/**"), Set.of("ADMR", "CENT_ADMR")),
            new Rule(List.of("/master/**"), Set.of("ADMR")),
            new Rule(List.of("/strategy/**"), Set.of("ADMR", "CENT_ADMR")),
            new Rule(List.of("/oms/**"), Set.of("ADMR", "ODR_PIC")),
            new Rule(List.of("/inbound/**"), Set.of("ADMR", "CENT_ADMR", "IB_PIC")),
            new Rule(List.of("/inventory/**"), Set.of("ADMR", "CENT_ADMR", "INV_PIC")),
            new Rule(List.of("/outbound/**"), Set.of("ADMR", "CENT_ADMR", "OUTB_PIC")));

    private SecurityRules() {
    }

    public record Rule(List<String> patterns, Set<String> roles) {
    }

    /**
     * 이 역할이 이 경로에 쓰기를 할 수 있나. 메뉴 권한 화면의 「저장까지 / 조회만」 판정이 여기다.
     * 규칙표에 없는 경로는 {@code denyAll}이라 false다.
     */
    public static boolean canWrite(String roleName, String path) {
        if (path == null) {
            return false;
        }
        // /master/usrs는 체인에서 GET 규칙보다 위에 손으로 두는 특례라 여기에도 따로 적는다
        if (matches("/master/usrs/**", path)) {
            return "ADMR".equals(roleName);
        }
        for (Rule rule : WRITE_RULES) {
            for (String pattern : rule.patterns()) {
                if (matches(pattern, path)) {
                    return rule.roles().contains(roleName);
                }
            }
        }
        return false;
    }

    /** {@code /a/**}는 {@code /a}와 {@code /a/...}에 걸린다. 세그먼트 경계를 지켜 /a가 /ab에 걸리지 않게 한다 */
    private static boolean matches(String pattern, String path) {
        if (!pattern.endsWith("/**")) {
            return pattern.equals(path);
        }
        String base = pattern.substring(0, pattern.length() - 3);
        return path.equals(base) || path.startsWith(base + "/");
    }
}
