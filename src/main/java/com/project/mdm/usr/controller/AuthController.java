package com.project.mdm.usr.controller;

import com.project.common.security.AuthUser;
import com.project.mdm.usr.dto.AuthDtos.LoginRequest;
import com.project.mdm.usr.dto.AuthDtos.LoginResponse;
import com.project.mdm.usr.dto.AuthDtos.MeResponse;
import com.project.mdm.usr.dto.AuthDtos.PwdChangeRequest;
import com.project.mdm.usr.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** 새로고침 시 토큰 유효성 확인을 겸한다. DB를 타지 않고 토큰 claim을 그대로 돌려준다 */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return new MeResponse(user.loginId(), user.usrNm(), user.roles());
    }

    @PutMapping("/pwd")
    public void changePwd(@RequestBody PwdChangeRequest request) {
        authService.changePwd(request);
    }
}
