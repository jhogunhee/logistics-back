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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UsrService service;

    @BeforeEach
    void setUp() {
        service = new UsrService(usrRepository, passwordEncoder);
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
    @DisplayName("관리자가 둘이면 한 명의 역할은 바꿀 수 있다")
    void allowsRoleChangeWhenAnotherAdmrExists() {
        Usr usr = usr("admin2", "부관리자", Role.ADMR);
        when(usrRepository.findById(2L)).thenReturn(Optional.of(usr));
        when(usrRepository.countByRole(Role.ADMR)).thenReturn(2L);

        service.saveAll(List.of(row("U", 2L, "admin2", "부관리자", null, List.of("INQ"))));

        assertEquals(Set.of(Role.INQ), usr.getRoles());
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

        service.saveAll(List.of(row("U", 3L, "stock", "재고담당김", "5678", List.of("INV_PIC"))));

        assertNotEquals(before, usr.getPwd());
        assertTrue(passwordEncoder.matches("5678", usr.getPwd()));
    }

    @Test
    @DisplayName("역할이 하나도 없는 행은 저장하지 않는다")
    void rejectsEmptyRoles() {
        assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", null, "nobody", "역할없음", "1234", List.of()))));
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
