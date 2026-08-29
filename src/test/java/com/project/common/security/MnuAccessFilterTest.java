package com.project.common.security;

import com.project.common.config.CorsConfig;
import com.project.common.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인가 ②단계의 명세. ①(SecurityConfig의 업무 구역 상한)을 통과한 요청에 대해서만
 * 메뉴가 켜져 있는지를 다시 본다 — 둘 다 통과해야 열린다.
 * <p>
 * 상한 자체는 {@code SecurityRulesTest}가 본다. 여기서는 {@code MnuAccessSource}를 가짜로 두고
 * <b>「꺼져 있을 때 무엇이 막히고 무엇이 안 막히는가」</b>만 고정한다.
 */
@WebMvcTest(controllers = MnuAccessFilterTest.ProbeController.class)
@Import({SecurityConfig.class, CorsConfig.class, MnuAccessFilterTest.ProbeController.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:5173")
class MnuAccessFilterTest {

    /** 스캔 없는 루트 설정 — 이유는 SecurityRulesTest.TestApp에 적어 뒀다 */
    @SpringBootConfiguration
    static class TestApp {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/inventory/stock") void stock() {}
        @PostMapping("/inventory/adjs") void adjust() {}
        @PostMapping("/master/prods/bulk") void prodSave() {}
    }

    @Autowired MockMvc mvc;
    @MockitoBean MnuAccessSource mnuAccessSource;

    @Test
    @DisplayName("메뉴가 꺼진 역할은 상한을 통과해도 막힌다")
    void blockedWhenMenuOff() throws Exception {
        given(mnuAccessSource.allows(anyList(), eq("/inventory/adjs"))).willReturn(false);

        mvc.perform(post("/inventory/adjs").with(authUser("INV_PIC")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("메뉴가 켜져 있으면 통과한다")
    void passesWhenMenuOn() throws Exception {
        given(mnuAccessSource.allows(anyList(), any())).willReturn(true);

        mvc.perform(post("/inventory/adjs").with(authUser("INV_PIC")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET은 메뉴가 꺼져 있어도 통과한다 — 화면 여럿이 같은 조회 API를 쓴다")
    void getIsNeverBlocked() throws Exception {
        given(mnuAccessSource.allows(anyList(), any())).willReturn(false);

        mvc.perform(get("/inventory/stock").with(authUser("INV_PIC")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMR은 메뉴가 꺼져 있어도 통과한다 — 잠김 방지")
    void admrBypasses() throws Exception {
        given(mnuAccessSource.allows(anyList(), any())).willReturn(false);

        mvc.perform(post("/master/prods/bulk").with(authUser("ADMR")).with(csrf()))
                .andExpect(status().isOk());
    }

    /** 실제 로그인과 같은 형태로 인증을 심는다 — 필터가 AuthUser에서 역할을 읽는다 */
    private static RequestPostProcessor authUser(String... roles) {
        AuthUser principal = new AuthUser("tester", "테스터", List.of(roles));
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
