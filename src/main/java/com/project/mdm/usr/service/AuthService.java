package com.project.mdm.usr.service;

import com.project.common.security.AuthUser;
import com.project.mdm.usr.dto.AuthDtos.LoginRequest;
import com.project.mdm.usr.dto.AuthDtos.PwdChangeRequest;
import com.project.mdm.usr.entity.Role;
import com.project.mdm.usr.entity.Usr;
import com.project.mdm.usr.repository.UsrRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UsrRepository usrRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;

    /**
     * 아이디·비밀번호를 확인하고 세션에 인증을 심는다.
     *
     * <p>로그인 실패를 401이 아니라 400으로 돌려준다 — 프론트 인터셉터가 401을 「세션이 끊겼다」로
     * 보고 /login으로 보내버려서, 로그인 화면에서 401이 나면 사유 토스트 없이 화면만 새로 뜬다.
     */
    public AuthUser login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // 아이디·비밀번호 누락은 실패와 같은 문구로 끊는다 — null을 그대로 넘기면 PasswordEncoder가
        // 내부 예외 문구("rawPassword cannot be null")를 뱉고, 그게 미인증 경로의 응답으로 나간다
        if (request == null || request.loginId() == null || request.loginId().isBlank() || request.pwd() == null) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        Usr usr = usrRepository.findByLoginId(request.loginId().trim())
                .filter(found -> passwordEncoder.matches(request.pwd(), found.getPwd()))
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        AuthUser authUser = toAuthUser(usr);
        establishSession(authUser, httpRequest, httpResponse);
        return authUser;
    }

    @Transactional
    public void changePwd(PwdChangeRequest request) {
        AuthUser me = AuthUser.current()
                .orElseThrow(() -> new IllegalStateException("로그인 정보를 확인할 수 없습니다."));
        if (request.newPwd() == null || request.newPwd().isBlank()) {
            throw new IllegalArgumentException("새 비밀번호는 필수입니다.");
        }
        Usr.validateRawPwd(request.newPwd());
        Usr usr = usrRepository.findByLoginId(me.loginId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + me.loginId()));
        if (!passwordEncoder.matches(request.curPwd(), usr.getPwd())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        usr.changePwd(passwordEncoder.encode(request.newPwd()));
    }

    /**
     * 기존 세션을 버리고 새 세션에 인증을 심는다 (세션 고정 공격 방지 — 로그인 전에 쥐고 있던
     * 세션 id가 로그인 후에도 유효하면 그 id를 심어둔 쪽이 남의 로그인을 물려받는다).
     */
    private void establishSession(AuthUser authUser, HttpServletRequest request, HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        List<SimpleGrantedAuthority> authorities = authUser.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(authUser, null, authorities));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private static AuthUser toAuthUser(Usr usr) {
        return new AuthUser(usr.getLoginId(), usr.getUsrNm(), usr.getRoles().stream().map(Role::name).toList());
    }
}
