package com.project.mdm.usr.service;

import com.project.common.security.AuthUser;
import com.project.mdm.usr.dto.UsrSaveRequest;
import com.project.mdm.usr.entity.Role;
import com.project.mdm.usr.entity.Usr;
import com.project.mdm.usr.repository.UsrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사용자 저장의 검증 명세. DB를 봐야 하는 판정 넷을 고정한다 —
 * 아이디 중복 · 자기 자신 삭제 · 마지막 시스템관리자 · 수정 행의 빈 비밀번호.
 * <p>
 * PasswordEncoder는 목이 아니라 실물이다. "빈 값이면 유지"는 해시가 실제로 안 바뀌는지로
 * 확인해야 의미가 있어서 인코딩을 흉내내지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsrServiceTest {

    @Mock UsrRepository usrRepository;
    @Mock FindByIndexNameSessionRepository<Session> sessionRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UsrService service;

    @BeforeEach
    void setUp() {
        service = new UsrService(usrRepository, passwordEncoder, sessionRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("이미 쓰는 아이디로 신규 등록하면 uq 위반 전에 사람 말로 거절한다")
    void rejectsDuplicateLoginId() {
        when(usrRepository.existsByLoginId("inbound")).thenReturn(true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", null, "inbound", "입고담당", "1234", List.of("IB_PIC")))));

        assertTrue(e.getMessage().contains("이미 쓰고 있는 아이디"));
        verify(usrRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("로그인한 사람이 자기 계정을 삭제할 수는 없다")
    void rejectsSelfDelete() {
        Usr me = usr("center", "센터관리자", Role.CENT_ADMR);
        when(usrRepository.findById(7L)).thenReturn(Optional.of(me));
        login("center");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("D", 7L, "center", "센터관리자", null, List.of("CENT_ADMR")))));

        assertTrue(e.getMessage().contains("자기 자신"));
        verify(usrRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("시스템관리자가 한 명뿐이면 그 계정을 지울 수 없다 — 지우면 사용자 관리에 아무도 못 들어간다")
    void rejectsDeletingLastAdmr() {
        when(usrRepository.findById(1L)).thenReturn(Optional.of(usr("admin", "시스템관리자", Role.ADMR)));
        when(usrRepository.countByRole(Role.ADMR)).thenReturn(1L);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("D", 1L, "admin", "시스템관리자", null, List.of("ADMR")))));

        assertTrue(e.getMessage().contains("마지막 시스템관리자"));
        verify(usrRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("마지막 시스템관리자의 ADMR 역할을 빼는 것도 삭제와 같이 막는다")
    void rejectsRemovingLastAdmrRole() {
        when(usrRepository.findById(1L)).thenReturn(Optional.of(usr("admin", "시스템관리자", Role.ADMR)));
        when(usrRepository.countByRole(Role.ADMR)).thenReturn(1L);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("U", 1L, "admin", "시스템관리자", null, List.of("INQ")))));

        assertTrue(e.getMessage().contains("마지막 시스템관리자"));
    }

    @Test
    @DisplayName("관리자가 둘이어도 자기 자신의 ADMR은 뺄 수 없다 — 시스템은 멀쩡한데 누른 사람만 갇힌다")
    void rejectsRemovingOwnAdmrRole() {
        Usr me = usr("admin", "시스템관리자", Role.ADMR);
        when(usrRepository.findById(1L)).thenReturn(Optional.of(me));
        // 관리자가 둘이라 「마지막 관리자」 가드는 통과한다 — 그래도 막혀야 한다는 것이 이 테스트다
        when(usrRepository.countByRole(Role.ADMR)).thenReturn(2L);
        login("admin");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("U", 1L, "admin", "시스템관리자", null, List.of("INQ")))));

        assertTrue(e.getMessage().contains("자기 자신"));
        assertEquals(Set.of(Role.ADMR), me.getRoles());
        verify(sessionRepository, never()).findByPrincipalName("admin");
    }

    @Test
    @DisplayName("남의 ADMR은 뗄 수 있다 — 막는 것은 자기 것뿐이라 다른 관리자가 해주면 된다")
    void allowsRemovingAnotherAdmrRole() {
        Usr other = usr("admin2", "부관리자", Role.ADMR);
        when(usrRepository.findById(2L)).thenReturn(Optional.of(other));
        when(usrRepository.countByRole(Role.ADMR)).thenReturn(2L);
        login("admin");

        service.saveAll(List.of(row("U", 2L, "admin2", "부관리자", null, List.of("INQ"))));

        assertEquals(Set.of(Role.INQ), other.getRoles());
    }

    @Test
    @DisplayName("자기 계정이라도 ADMR을 그대로 둔 채 다른 역할을 더하는 것은 된다")
    void allowsOwnRoleChangeThatKeepsAdmr() {
        Usr me = usr("admin", "시스템관리자", Role.ADMR);
        when(usrRepository.findById(1L)).thenReturn(Optional.of(me));
        login("admin");

        service.saveAll(List.of(row("U", 1L, "admin", "시스템관리자", null, List.of("ADMR", "INV_PIC"))));

        assertEquals(Set.of(Role.ADMR, Role.INV_PIC), me.getRoles());
    }

    @Test
    @DisplayName("관리자가 둘이면 한 명의 역할은 바꿀 수 있다")
    void allowsRoleChangeWhenAnotherAdmrExists() {
        Usr usr = usr("admin2", "부관리자", Role.ADMR);
        when(usrRepository.findById(2L)).thenReturn(Optional.of(usr));
        when(usrRepository.countByRole(Role.ADMR)).thenReturn(2L);

        service.saveAll(List.of(row("U", 2L, "admin2", "부관리자", null, List.of("INQ"))));

        assertEquals(Set.of(Role.INQ), usr.getRoles());
    }

    @Test
    @DisplayName("역할이 바뀌면 그 사람의 세션을 끊는다 — 안 끊으면 방금 뺏은 권한으로 계속 돈다")
    void roleChangeExpiresSessions() {
        Usr usr = usr("admin2", "부관리자", Role.ADMR);
        when(usrRepository.findById(2L)).thenReturn(Optional.of(usr));
        when(usrRepository.countByRole(Role.ADMR)).thenReturn(2L);
        when(sessionRepository.findByPrincipalName("admin2"))
                .thenReturn(Map.of("sess-1", mock(Session.class), "sess-2", mock(Session.class)));

        service.saveAll(List.of(row("U", 2L, "admin2", "부관리자", null, List.of("INQ"))));

        verify(sessionRepository).deleteById("sess-1");
        verify(sessionRepository).deleteById("sess-2");
    }

    @Test
    @DisplayName("역할이 그대로면 세션은 건드리지 않는다 — 이름만 고쳤다고 내보낼 이유가 없다")
    void nameOnlyChangeKeepsSessions() {
        Usr usr = usr("stock", "재고담당", Role.INV_PIC);
        when(usrRepository.findById(3L)).thenReturn(Optional.of(usr));

        service.saveAll(List.of(row("U", 3L, "stock", "재고담당김", null, List.of("INV_PIC"))));

        verify(sessionRepository, never()).findByPrincipalName(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("삭제된 사용자의 세션도 끊는다 — 계정이 없는데 세션이 살아 있으면 안 된다")
    void deleteExpiresSessions() {
        Usr usr = usr("viewer", "조회전용", Role.INQ);
        when(usrRepository.findById(9L)).thenReturn(Optional.of(usr));
        when(sessionRepository.findByPrincipalName("viewer")).thenReturn(Map.of("sess-9", mock(Session.class)));

        service.saveAll(List.of(row("D", 9L, "viewer", "조회전용", null, List.of("INQ"))));

        verify(usrRepository).delete(usr);
        verify(sessionRepository).deleteById("sess-9");
    }

    @Test
    @DisplayName("수정 행의 비밀번호가 비어 있으면 기존 해시를 그대로 둔다 (입력한 경우에만 초기화)")
    void keepsPasswordWhenBlank() {
        Usr usr = usr("stock", "재고담당", Role.INV_PIC);
        String before = usr.getPwd();
        when(usrRepository.findById(3L)).thenReturn(Optional.of(usr));

        service.saveAll(List.of(row("U", 3L, "stock", "재고담당김", "", List.of("INV_PIC"))));

        assertEquals(before, usr.getPwd());
        assertEquals("재고담당김", usr.getUsrNm());

        service.saveAll(List.of(row("U", 3L, "stock", "재고담당김", "NewPwd!2026", List.of("INV_PIC"))));

        assertNotEquals(before, usr.getPwd());
        assertTrue(passwordEncoder.matches("NewPwd!2026", usr.getPwd()));
    }

    @Test
    @DisplayName("8자 미만 비밀번호는 신규·수정 어느 쪽으로도 저장되지 않는다 — 화면에서 1234를 다시 넣는 길을 막는다")
    void rejectsShortPassword() {
        when(usrRepository.findById(3L)).thenReturn(Optional.of(usr("stock", "재고담당", Role.INV_PIC)));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", null, "newbie", "신규", "1234", List.of("INQ")))))
                .getMessage().contains("8자 이상"));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("U", 3L, "stock", "재고담당", "1234", List.of("INV_PIC")))))
                .getMessage().contains("8자 이상"));
    }

    @Test
    @DisplayName("역할이 하나도 없는 행은 저장하지 않는다")
    void rejectsEmptyRoles() {
        // 메시지까지 본다 — 이 행은 비밀번호도 짧아서, 안 보면 「8자 이상」으로 통과해도 초록이다
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", null, "nobody", "역할없음", "1234", List.of()))))
                .getMessage().contains("역할은 하나 이상"));
    }

    @Test
    @DisplayName("역할 목록이 아예 없어도 500이 아니라 사람 말로 거절한다 — 수정 판정이 검증보다 먼저 이 값을 본다")
    void rejectsNullRoles() {
        when(usrRepository.findById(3L)).thenReturn(Optional.of(usr("stock", "재고담당", Role.INV_PIC)));

        // 신규 · 수정 두 경로 모두. 수정 쪽이 원래 NPE가 나던 자리다
        // (UsrService.update가 「관리자 역할을 떼는가」를 보려고 updateEntity보다 먼저 toRoles를 부른다)
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", null, "newbie", "신규", "GoodPwd!2026", null))))
                .getMessage().contains("역할은 하나 이상"));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("U", 3L, "stock", "재고담당", null, null))))
                .getMessage().contains("역할은 하나 이상"));
    }

    private Usr usr(String loginId, String usrNm, Role... roles) {
        return Usr.builder()
                .loginId(loginId)
                .usrNm(usrNm)
                .pwd(passwordEncoder.encode("1234"))
                .roles(Set.of(roles))
                .build();
    }

    private static UsrSaveRequest row(String status, Long usrId, String loginId, String usrNm,
                                      String pwd, List<String> roles) {
        UsrSaveRequest request = new UsrSaveRequest();
        request.setStatus(status);
        request.setUsrId(usrId);
        request.setLoginId(loginId);
        request.setUsrNm(usrNm);
        request.setPwd(pwd);
        request.setRoles(roles);
        return request;
    }

    private static void login(String loginId) {
        AuthUser me = new AuthUser(loginId, loginId, List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(me, null, List.of()));
    }
}
