package com.project.mdm.usr.controller;

import com.project.common.security.AuthUser;
import com.project.mdm.mnu.service.MnuService;
import com.project.mdm.usr.dto.AuthDtos.LoginRequest;
import com.project.mdm.usr.dto.AuthDtos.LoginResponse;
import com.project.mdm.usr.dto.AuthDtos.MeResponse;
import com.project.mdm.usr.dto.AuthDtos.PwdChangeRequest;
import com.project.mdm.usr.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그아웃은 여기 없다 — SecurityConfig의 logout 설정이 /auth/logout을 처리한다(세션 무효화 포함). */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MnuService mnuService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse,
                               CsrfToken csrfToken) {
        AuthUser user = authService.login(request, httpRequest, httpResponse);
        // 토큰은 세션이 만들어진 뒤에 읽는다 — 새 세션에 저장돼야 이후 요청과 짝이 맞는다
        return new LoginResponse(user.loginId(), user.usrNm(), user.roles(), csrfToken.getToken(),
                mnuService.menusOf(user.roles()));
    }

    /** 새로고침 시 세션 유효성 확인을 겸한다. 사용자 정보는 세션에서, 메뉴는 그때그때 DB에서 읽는다 */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser user, CsrfToken csrfToken) {
        return new MeResponse(user.loginId(), user.usrNm(), user.roles(), csrfToken.getToken(),
                mnuService.menusOf(user.roles()));
    }

    @PutMapping("/pwd")
    public void changePwd(@RequestBody PwdChangeRequest request) {
        authService.changePwd(request);
    }
}
