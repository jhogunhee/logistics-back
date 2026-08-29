package com.project.mdm.mnu.service;

import com.project.mdm.mnu.dto.MnuSaveRequest;
import com.project.mdm.mnu.entity.Mnu;
import com.project.mdm.mnu.entity.MnuDvsn;
import com.project.mdm.mnu.repository.MnuRepository;
import com.project.mdm.mnu.repository.MnuRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 메뉴 저장의 검증 명세. DB를 봐야 하는 판정만 여기 있다 —
 * 필수·형식 검사는 {@code MnuSaveRequest}가 자기 필드만으로 하므로 이 테스트의 관심이 아니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MnuServiceTest {

    @Mock MnuRepository mnuRepository;
    @Mock MnuRoleRepository mnuRoleRepository;
    @Mock MnuAccessCache mnuAccessCache;

    private MnuService service;

    @BeforeEach
    void setUp() {
        service = new MnuService(mnuRepository, mnuRoleRepository, mnuAccessCache);
    }

    @Test
    @DisplayName("화면 경로가 겹치면 거부한다 — 사이드바에 같은 화면이 둘 뜬다")
    void rejectsDuplicateScrnPth() {
        when(mnuRepository.existsByScrnPth("/stock/spmt")).thenReturn(true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", "NEW", "새 화면", "/stock/spmt", "/inventory/x"))));

        assertTrue(e.getMessage().contains("화면 경로"));
    }

    @Test
    @DisplayName("API 접두가 겹쳐도 받는다 — 같은 API를 나눠 쓰는 화면이 정상적으로 여럿이다")
    void allowsDuplicateApiPrfx() {
        assertDoesNotThrow(
                () -> service.saveAll(List.of(row("C", "NEW", "새 화면", "/stock/new", "/inventory/adjs"))));

        verify(mnuRepository).save(any(Mnu.class));
    }

    @Test
    @DisplayName("메뉴 코드가 겹치면 거부한다")
    void rejectsDuplicateMnuCd() {
        when(mnuRepository.existsById("NEW")).thenReturn(true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", "NEW", "새 화면", "/stock/new", null))));

        assertTrue(e.getMessage().contains("메뉴 코드"));
    }

    @Test
    @DisplayName("메뉴를 지우면 그 메뉴의 권한 행도 함께 지운다 — FK가 없어 DB가 안 치운다")
    void deleteAlsoRemovesRoles() {
        when(mnuRepository.findById("OLD")).thenReturn(Optional.of(mnu("OLD")));

        service.saveAll(List.of(row("D", "OLD", null, null, null)));

        verify(mnuRoleRepository).deleteByMnuCdIn(List.of("OLD"));
    }

    private static MnuSaveRequest row(String status, String mnuCd, String mnuNm,
                                      String scrnPth, String apiPrfx) {
        MnuSaveRequest row = new MnuSaveRequest();
        row.setStatus(status);
        row.setMnuCd(mnuCd);
        row.setMnuNm(mnuNm);
        row.setDvsn(MnuDvsn.WEB);
        row.setGrpNm("재고");
        row.setSrtSeq(390);
        row.setIconNm("Box");
        row.setScrnPth(scrnPth);
        row.setApiPrfx(apiPrfx);
        return row;
    }

    private static Mnu mnu(String mnuCd) {
        return Mnu.builder()
                .mnuCd(mnuCd)
                .mnuNm("옛 화면")
                .dvsn(MnuDvsn.WEB)
                .grpNm("재고")
                .srtSeq(390)
                .iconNm("Box")
                .scrnPth("/stock/old")
                .build();
    }
}
