package com.project.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** canWrite가 SecurityConfig 체인과 같은 답을 내는지 — 「조회만 👁」 표시가 거짓말하지 않는 근거 */
class SecurityRulesCanWriteTest {

    @Test
    @DisplayName("먼저 걸리는 규칙이 이긴다 — 로케이션은 /master/** 보다 위의 줄에 걸린다")
    void longerPrefixWinsByOrder() {
        assertTrue(SecurityRules.canWrite("CENT_ADMR", "/master/locs/bulk"));
        assertFalse(SecurityRules.canWrite("CENT_ADMR", "/master/prods/bulk"));
    }

    @Test
    @DisplayName("고정로케이션만 재고담당에게 열려 있다")
    void invPicOnlyOnFxngLoc() {
        assertTrue(SecurityRules.canWrite("INV_PIC", "/master/fxng-locs/bulk"));
        assertFalse(SecurityRules.canWrite("INV_PIC", "/master/locs/bulk"));
    }

    @Test
    @DisplayName("사용자 마스터는 관리자만 — 체인에서 GET 규칙보다 위에 있는 특례")
    void usrMasterIsAdmrOnly() {
        assertTrue(SecurityRules.canWrite("ADMR", "/master/usrs/bulk"));
        assertFalse(SecurityRules.canWrite("CENT_ADMR", "/master/usrs/bulk"));
    }

    @Test
    @DisplayName("규칙표에 없는 접두는 관리자라도 false — denyAll과 같은 답")
    void unlistedPrefixIsFalse() {
        assertFalse(SecurityRules.canWrite("ADMR", "/nowhere"));
    }

    @Test
    @DisplayName("접두는 세그먼트 경계를 지킨다 — /master/loc이 /master/locs에 걸리면 안 된다")
    void prefixRespectsSegmentBoundary() {
        assertFalse(SecurityRules.canWrite("CENT_ADMR", "/master/locsomething/bulk"));
    }

    @Test
    @DisplayName("조회 전용 화면은 경로가 없다 — null은 쓰기 불가")
    void nullPathIsFalse() {
        assertFalse(SecurityRules.canWrite("ADMR", null));
    }

    @Test
    @DisplayName("규칙표에 없는 역할명은 걸리는 경로에서도 false")
    void unknownRoleNameIsFalse() {
        assertFalse(SecurityRules.canWrite("NOPE", "/master/locs/bulk"));
    }
}
