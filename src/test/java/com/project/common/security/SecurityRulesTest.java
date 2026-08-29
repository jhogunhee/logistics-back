package com.project.common.security;

import com.project.common.config.CorsConfig;
import com.project.common.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig의 URL 규칙표를 그대로 검증한다. 규칙이 한 곳에 모여 있는 것이 이 설계의 전제라,
 * 그 한 곳이 무너지는지를 보는 테스트도 하나여야 한다.
 * <p>
 * DB를 올리지 않으려고 {@code @WebMvcTest} 슬라이스를 쓴다 — {@code @SpringBootTest}는
 * DataSource와 EntityManagerFactory를 올려 Supabase 접속을 요구한다.
 * <p>
 * {@code /health} · preflight · CSRF 케이스는 회귀 방지용이다. 셋 다 「인증을 켠 순간 화면이
 * 안 돈다」거나 「켰는데 안 막힌다」로 이어지는 자리라, 규칙을 손볼 때 먼저 깨져야 한다.
 */
@WebMvcTest(controllers = SecurityRulesTest.ProbeController.class)
@Import({SecurityConfig.class, CorsConfig.class, SecurityRulesTest.ProbeController.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:5173")
class SecurityRulesTest {

    /**
     * 슬라이스의 루트 설정. 앱 클래스가 {@code com.project.wmsback}에 있어 이 패키지에서 위로
     * 올라가도 안 나오고, 그렇다고 앱 클래스를 쓰면 컴포넌트 스캔이 서비스·리포지토리를 전부
     * 끌어와 DB를 요구한다. 스캔 없는 빈 설정을 두고 필요한 것만 {@code @Import}한다.
     */
    @SpringBootConfiguration
    static class TestApp {
    }

    /** 규칙표의 접두마다 하나씩. 실제 컨트롤러는 서비스 빈을 요구해 슬라이스에 올릴 수 없다 */
    @RestController
    static class ProbeController {
        @GetMapping("/health") void health() {}
        @PostMapping("/auth/login") void login() {}
        @PostMapping("/auth/scan-login") void scanLogin() {}
        @GetMapping("/wrkr/acrst/summary") void wrkrAcrst() {}
        // 실제 AuthController.me와 같이 CSRF 토큰을 돌려준다 — 프론트가 이 값을 헤더로 되던진다
        @GetMapping("/auth/me") String me(CsrfToken token) { return token.getToken(); }
        @GetMapping("/master/vendors") void vendorList() {}
        @PostMapping("/master/vendors/bulk") void vendorSave() {}
        @GetMapping("/master/usrs") void usrList() {}
        @PostMapping("/master/usrs/bulk") void usrSave() {}
        // /master 접두를 쓰지만 규칙이 다른 둘 — 창고 물리 구조와 재보충 기준
        @PostMapping("/master/locs/bulk") void locSave() {}
        @PostMapping("/master/fxng-locs/bulk") void fxngLocSave() {}
        @PostMapping("/inbound/receivings") void receive() {}
        @PostMapping("/outbound/waves") void wave() {}
        @PostMapping("/nowhere") void unlistedPrefix() {}
    }

    @Autowired MockMvc mvc;

    /** 메뉴 권한(②단계)은 MnuAccessFilterTest가 본다 — 여기서는 상한만 보게 늘 통과시킨다 */
    @MockitoBean MnuAccessSource mnuAccessSource;

    @BeforeEach
    void allowEveryMenu() {
        given(mnuAccessSource.allows(anyList(), any())).willReturn(true);
    }

    @Test
    @DisplayName("/health는 로그인 없이 열려 있다 — GET만이 아니라 HEAD도. 슬립 방지 크론이 HEAD로 부른다")
    void healthIsOpen() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk());
        // 메서드를 GET으로 한정했다가 크론의 HEAD가 401로 막혀 keep-alive가 죽은 적이 있다.
        // GET만 검증하던 이 테스트가 그걸 통과시켰다 — 그래서 메서드를 나눠 본다
        mvc.perform(head("/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인은 세션도 CSRF 토큰도 없이 열려 있다 — 둘 다 로그인이 만들어 주는 것이다")
    void loginIsOpen() throws Exception {
        mvc.perform(post("/auth/login")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PDA 간편 로그인도 세션·CSRF 없이 열려 있다 — 로그인과 같은 자리다")
    void scanLoginIsOpen() throws Exception {
        mvc.perform(post("/auth/scan-login")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("작업자 실적은 조회도 관리자·센터관리자만이다 — GET 규칙보다 먼저 걸려야 한다")
    void wrkrAcrstIsCenterOnly() throws Exception {
        mvc.perform(get("/wrkr/acrst/summary").with(user("tester").roles("INQ")))
                .andExpect(status().isForbidden());
        // 현장 담당에게도 열리면 안 된다 — 개인별 생산성이라 업무 권한과 다른 축이다
        mvc.perform(get("/wrkr/acrst/summary").with(user("tester").roles("OUTB_PIC")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/wrkr/acrst/summary").with(user("tester").roles("CENT_ADMR")))
                .andExpect(status().isOk());
        mvc.perform(get("/wrkr/acrst/summary").with(user("tester").roles("ADMR")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CORS preflight(OPTIONS)는 로그인 없이 통과한다 — 막히면 모든 비GET이 브라우저에서 CORS 오류가 된다")
    void preflightPasses() throws Exception {
        mvc.perform(options("/master/vendors")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/master/vendors")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF 토큰이 없는 저장 요청은 로그인했어도 막힌다 — 쿠키 인증의 전제")
    void writeWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/inbound/receivings").with(user("tester").roles("IB_PIC")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("저장 요청은 X-CSRF-TOKEN 헤더로 통과한다 — 이름이 바뀌면 프론트의 전 저장이 403이 된다")
    void writeWithCsrfHeaderPasses() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 토큰은 세션 저장소에 있다. /auth/me가 그 값을 본문으로 주고, 프론트는 그걸 헤더에 싣는다
        String token = mvc.perform(get("/auth/me").session(session).with(user("tester").roles("IB_PIC")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 헤더 이름을 리터럴로 박는 것이 이 테스트의 전부다 — .with(csrf())는 서버 설정에서 방식을
        // 읽어 알아서 넣어주므로 이름이 어긋나도 통과한다(실제로 X-XSRF-TOKEN 오기를 못 잡았다)
        mvc.perform(post("/inbound/receivings").session(session)
                        .with(user("tester").roles("IB_PIC"))
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("조회 역할은 모든 GET을 볼 수 있다")
    void inqCanRead() throws Exception {
        mvc.perform(get("/master/vendors").with(user("tester").roles("INQ")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("조회 역할은 저장을 못 한다")
    void inqCannotWrite() throws Exception {
        mvc.perform(post("/master/vendors/bulk").with(user("tester").roles("INQ")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("입고담당은 /inbound 저장이 되고 /outbound 저장은 안 된다")
    void ibPicIsScopedToInbound() throws Exception {
        mvc.perform(post("/inbound/receivings").with(user("tester").roles("IB_PIC")).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/outbound/waves").with(user("tester").roles("IB_PIC")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("여러 역할을 가지면 그중 하나만 맞아도 통과한다 (입고+재고 겸직)")
    void multipleRolesPassIfAnyMatches() throws Exception {
        mvc.perform(post("/inbound/receivings").with(user("manager").roles("IB_PIC", "INV_PIC")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("사용자 관리는 조회도 시스템관리자만이다")
    void usrMasterIsAdmrOnly() throws Exception {
        mvc.perform(get("/master/usrs").with(user("tester").roles("INQ")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/master/usrs").with(user("tester").roles("ADMR")))
                .andExpect(status().isOk());
        mvc.perform(post("/master/usrs/bulk").with(user("tester").roles("ADMR")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("센터관리자는 전략을 만지지만 마스터는 못 만진다")
    void centAdmrCannotTouchMaster() throws Exception {
        mvc.perform(post("/master/vendors/bulk").with(user("tester").roles("CENT_ADMR")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("창고 물리 구조는 센터관리자도 만진다 — 같은 /master 접두라 위 규칙보다 먼저 걸려야 한다")
    void centAdmrCanTouchWarehouseMaster() throws Exception {
        mvc.perform(post("/master/locs/bulk").with(user("tester").roles("CENT_ADMR")).with(csrf()))
                .andExpect(status().isOk());
        // 넓힌 것은 센터관리자까지다 — 업무담당에게까지 열리면 규칙이 새는 것이다
        mvc.perform(post("/master/locs/bulk").with(user("tester").roles("IB_PIC")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("고정로케이션은 재고담당도 만진다 — 이 값으로 도는 정기보충이 INV_PIC이라 짝을 맞춘다")
    void invPicCanTouchFxngLoc() throws Exception {
        mvc.perform(post("/master/fxng-locs/bulk").with(user("tester").roles("INV_PIC")).with(csrf()))
                .andExpect(status().isOk());
        // 재고담당에게 열린 것은 고정로케이션 하나다 — 창고 구조까지 따라 열리면 안 된다
        mvc.perform(post("/master/locs/bulk").with(user("tester").roles("INV_PIC")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("규칙표에 없는 접두의 비GET은 관리자라도 막힌다 (denyAll) — 새 컨트롤러가 접두를 어기면 여기서 걸린다")
    void unlistedPrefixIsDenied() throws Exception {
        mvc.perform(post("/nowhere").with(user("tester").roles("ADMR")).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
