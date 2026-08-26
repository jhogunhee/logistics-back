package com.project.mdm.usr.service;

import com.project.common.security.AuthUser;
import com.project.common.security.JwtTokenProvider;
import com.project.mdm.usr.dto.AuthDtos.LoginRequest;
import com.project.mdm.usr.dto.AuthDtos.LoginResponse;
import com.project.mdm.usr.dto.AuthDtos.PwdChangeRequest;
import com.project.mdm.usr.entity.Role;
import com.project.mdm.usr.entity.Usr;
import com.project.mdm.usr.repository.UsrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UsrRepository usrRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * 로그인 실패를 401이 아니라 400으로 돌려준다 — 프론트 인터셉터가 401을 「세션이 끊겼다」로
     * 보고 /login으로 보내버려서, 로그인 화면에서 401이 나면 사유 토스트 없이 화면만 새로 뜬다.
     */
    public LoginResponse login(LoginRequest request) {
        Usr usr = usrRepository.findByLoginId(request.loginId() == null ? "" : request.loginId().trim())
                .filter(found -> passwordEncoder.matches(request.pwd(), found.getPwd()))
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        AuthUser authUser = toAuthUser(usr);
        return new LoginResponse(tokenProvider.issue(authUser), authUser.loginId(), authUser.usrNm(), authUser.roles());
    }

    @Transactional
    public void changePwd(PwdChangeRequest request) {
        AuthUser me = AuthUser.current()
                .orElseThrow(() -> new IllegalStateException("로그인 정보를 확인할 수 없습니다."));
        if (request.newPwd() == null || request.newPwd().isBlank()) {
            throw new IllegalArgumentException("새 비밀번호는 필수입니다.");
        }
        Usr usr = usrRepository.findByLoginId(me.loginId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + me.loginId()));
        if (!passwordEncoder.matches(request.curPwd(), usr.getPwd())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        usr.changePwd(passwordEncoder.encode(request.newPwd()));
    }

    private static AuthUser toAuthUser(Usr usr) {
        return new AuthUser(usr.getLoginId(), usr.getUsrNm(), usr.getRoles().stream().map(Role::name).toList());
    }
}
