package com.project.mdm.mnu.service;

import com.project.mdm.mnu.dto.MnuResponse;
import com.project.mdm.mnu.dto.MnuRoleGridResponse;
import com.project.mdm.mnu.dto.MnuRoleSaveRequest;
import com.project.mdm.mnu.entity.Mnu;
import com.project.mdm.mnu.entity.MnuDvsn;
import com.project.mdm.mnu.entity.MnuRole;
import com.project.mdm.mnu.repository.MnuRepository;
import com.project.mdm.mnu.repository.MnuRoleRepository;
import com.project.mdm.usr.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 권한 격자의 명세. 「코드가 상한, DB가 실제」라 상한에 막히는 칸을 <b>막지 않고 표시만</b> 하는 것과,
 * 저장이 그 구분을 통째로 교체하는 것이 이 테스트가 고정하는 두 가지다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MnuRoleServiceTest {

    @Mock MnuRepository mnuRepository;
    @Mock MnuRoleRepository mnuRoleRepository;
    @Mock MnuAccessCache mnuAccessCache;

    private MnuService service;

    @BeforeEach
    void setUp() {
        service = new MnuService(mnuRepository, mnuRoleRepository, mnuAccessCache);
    }

    @Test
    @DisplayName("쓰기가 안 되는 역할은 readOnlyRoles로 표시한다 — 막지 않고 알려만 준다")
    void marksReadOnlyRoles() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("MST_LOC", "/master/locs")));
        when(mnuRoleRepository.findAllByMnuCdIn(List.of("MST_LOC")))
                .thenReturn(List.of(new MnuRole("MST_LOC", Role.INV_PIC)));

        MnuRoleGridResponse row = service.roleGrid(MnuDvsn.WEB).get(0);

        assertEquals(List.of("INV_PIC"), row.roles());
        // /master/locs는 ADMR·CENT_ADMR만 쓴다 — INV_PIC은 열리지만 저장은 못 한다
        assertEquals(List.of("INV_PIC"), row.readOnlyRoles());
    }

    @Test
    @DisplayName("조회 전용 화면(api_prfx 없음)은 readOnlyRoles가 비어 있다")
    void readOnlyScreenHasNoMarks() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("STK_STATUS", null)));
        when(mnuRoleRepository.findAllByMnuCdIn(List.of("STK_STATUS")))
                .thenReturn(List.of(new MnuRole("STK_STATUS", Role.INQ)));

        assertTrue(service.roleGrid(MnuDvsn.WEB).get(0).readOnlyRoles().isEmpty());
    }

    @Test
    @DisplayName("매핑 저장은 그 탭을 통째로 교체한다 — 두 번 눌러도 결과가 같다")
    void replaceIsIdempotent() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("A", "/inventory/a"), mnu("B", "/inventory/b")));

        service.replaceRoles(MnuDvsn.WEB, List.of(save("A", List.of("INV_PIC"))));

        verify(mnuRoleRepository).deleteByMnuCdIn(List.of("A", "B"));
        verify(mnuRoleRepository).saveAll(argThat(it ->
                StreamSupport.stream(it.spliterator(), false).count() == 1));
    }

    @Test
    @DisplayName("ADMR은 저장하지 않는다 — 매핑 대상이 아니고 DB CHECK도 막는다")
    void ignoresAdmr() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("A", "/inventory/a")));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.replaceRoles(MnuDvsn.WEB, List.of(save("A", List.of("ADMR")))));

        assertTrue(e.getMessage().contains("시스템관리자"));
    }

    @Test
    @DisplayName("그 구분에 없는 메뉴는 거부한다 — WEB 탭 저장이 PDA 매핑을 건드리면 안 된다")
    void rejectsMenuOutsideDvsn() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("A", "/inventory/a")));

        assertThrows(IllegalArgumentException.class,
                () -> service.replaceRoles(MnuDvsn.PDA, List.of(save("A", List.of("INV_PIC")))));
    }

    @Test
    @DisplayName("ADMR은 DB를 보지 않고 전 메뉴를 받는다")
    void admrGetsEveryMenu() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("A", null), mnu("B", null)));

        assertEquals(2, service.menusOf(List.of("ADMR")).size());
        verify(mnuRoleRepository, never()).findAllByMnuCdIn(any());
    }

    @Test
    @DisplayName("담당은 자기 역할에 켜진 메뉴만 받는다")
    void picGetsOnlyGrantedMenus() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("A", null), mnu("B", null)));
        when(mnuRoleRepository.findAllByMnuCdIn(List.of("A", "B")))
                .thenReturn(List.of(new MnuRole("A", Role.INV_PIC)));

        assertEquals(List.of("A"),
                service.menusOf(List.of("INV_PIC")).stream().map(MnuResponse::mnuCd).toList());
    }

    @Test
    @DisplayName("매핑을 저장하면 캐시를 다시 읽는다 — 다음 요청부터 바로 반영돼야 한다")
    void reloadsCacheAfterSave() {
        when(mnuRepository.findAllByOrderByGrpNmAscSrtSeqAsc())
                .thenReturn(List.of(mnu("A", "/inventory/a")));

        service.replaceRoles(MnuDvsn.WEB, List.of(save("A", List.of("INV_PIC"))));

        verify(mnuAccessCache).reload();
    }

    private static Mnu mnu(String mnuCd, String apiPrfx) {
        return Mnu.builder()
                .mnuCd(mnuCd)
                .mnuNm(mnuCd)
                .dvsn(MnuDvsn.WEB)
                .grpNm("재고")
                .srtSeq(300)
                .iconNm("Box")
                .scrnPth("/x/" + mnuCd)
                .apiPrfx(apiPrfx)
                .build();
    }

    private static MnuRoleSaveRequest save(String mnuCd, List<String> roles) {
        MnuRoleSaveRequest row = new MnuRoleSaveRequest();
        row.setMnuCd(mnuCd);
        row.setRoles(roles);
        return row;
    }
}
