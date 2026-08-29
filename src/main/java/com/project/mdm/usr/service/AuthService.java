package com.project.mdm.usr.service;

import com.project.common.security.AuthUser;
import com.project.mdm.usr.dto.AuthDtos.LoginRequest;
import com.project.mdm.usr.dto.AuthDtos.PwdChangeRequest;
import com.project.mdm.usr.dto.AuthDtos.ScanLoginRequest;
import com.project.mdm.usr.entity.Role;
import com.project.mdm.usr.entity.Usr;
import com.project.mdm.usr.repository.UsrRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    /** 아이디만으로 세션이 열리는 문을 여는 역할. 현장 실행 화면(/m)이 쓰는 권한이 전부다 */
    private static final Set<Role> FIELD_ROLES = EnumSet.of(Role.IB_PIC, Role.INV_PIC, Role.OUTB_PIC);

    private static final String SCAN_LOGIN_FAILED = "작업자 코드를 확인할 수 없습니다.";

    private final UsrRepository usrRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;

    /**
     * 스캔 세션의 유휴 만료. 전체 세션(12h)과 따로 두는 이유 — 비밀번호를 묻지 않고 연 세션이라
     * 단말을 두고 간 뒤 남아 있는 시간을 짧게 가져간다. 교대 중에는 요청이 계속 나가 끊기지 않는다.
     */
    @Value("${wms.pda.scan-session-timeout:PT4H}")
    private Duration scanSessionTimeout;

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

    /**
     * PDA 간편 로그인 — 작업자 코드(= 로그인 아이디) 바코드 한 번으로 세션을 연다.
     * 비밀번호를 묻지 않는 이유는 현장 단말의 사정이다: 장갑 낀 손으로 좁은 키패드에 비밀번호를
     * 치게 하면 실제로는 조 단위 공용 비번이 되어 「계정은 개인별인데 실질은 공용」이 된다.
     * 목적이 침입 차단이 아니라 <b>실적 귀속</b>이므로, 입력 비용을 없애는 쪽이 목적에 맞는다.
     *
     * <p><b>현장 역할만 연다</b>({@link #FIELD_ROLES}). 이 앱은 인터넷에 열려 있어, 아이디만으로
     * 세션이 열리는 문을 모든 계정에 두면 아이디를 아는 사람이 그대로 그 권한이 된다 —
     * 관리자·주문담당처럼 현장 실행 밖의 권한이 섞인 계정은 기존 비밀번호 경로로만 들어온다.
     *
     * <p>실패 문구를 하나로 두는 이유는 {@link #login}과 같다 — 없는 아이디와 막힌 아이디를
     * 문구로 갈라 주면 어떤 아이디가 존재하는지 확인해 주는 셈이 된다.
     */
    public AuthUser scanLogin(ScanLoginRequest request, HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        if (request == null || request.loginId() == null || request.loginId().isBlank()) {
            throw new IllegalArgumentException(SCAN_LOGIN_FAILED);
        }
        Usr usr = usrRepository.findByLoginId(request.loginId().trim())
                .filter(AuthService::scannable)
                .orElseThrow(() -> new IllegalArgumentException(SCAN_LOGIN_FAILED));

        AuthUser authUser = toAuthUser(usr);
        establishSession(authUser, httpRequest, httpResponse);
        // 세션이 만들어진 뒤라야 만료를 걸 수 있다. 스캔 세션만 짧게 두는 이유 —
        // 한 번 찍으면 로그아웃까지 유지되므로, 단말을 놓고 간 뒤를 끊는 것은 유휴 만료뿐이다
        httpRequest.getSession().setMaxInactiveInterval((int) scanSessionTimeout.toSeconds());
        return authUser;
    }

    /** 현장 실행 역할만으로 이루어진 계정인가. 역할이 없는 계정도 열지 않는다 */
    private static boolean scannable(Usr usr) {
        return !usr.getRoles().isEmpty() && FIELD_ROLES.containsAll(usr.getRoles());
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
