package com.project.mdm.nbr.service;

import com.project.mdm.nbr.dto.NbrRuleSaveRequest;
import com.project.mdm.nbr.entity.DyncKyTyp;
import com.project.mdm.nbr.entity.NbrRule;
import com.project.mdm.nbr.repository.NbrRuleRepository;
import com.project.mdm.nbr.repository.NbrSeqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NbrRuleServiceTest {

    @Mock
    private NbrRuleRepository nbrRuleRepository;
    @Mock
    private NbrSeqRepository nbrSeqRepository;

    @InjectMocks
    private NbrRuleService nbrRuleService;

    private NbrRuleSaveRequest createRow(String status, String ruleCd, String prfx, Integer seqDgt, DyncKyTyp typ) {
        NbrRuleSaveRequest row = new NbrRuleSaveRequest();
        row.setStatus(status);
        row.setRuleCd(ruleCd);
        row.setRuleNm("테스트 규칙");
        row.setPrfx(prfx);
        row.setPrfxDlmt("-");
        row.setDeDlmt("-");
        row.setSeqDgt(seqDgt);
        row.setDyncKyTyp(typ);
        return row;
    }

    @Test
    void 신규_등록시_이미_있는_ruleCd면_예외() {
        when(nbrRuleRepository.existsById("PROD_CD")).thenReturn(true);
        NbrRuleSaveRequest row = createRow("C", "PROD_CD", "PROD", 4, DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
        verify(nbrRuleRepository, never()).save(any());
    }

    @Test
    void 신규_등록시_prfx가_비어있으면_예외() {
        // 형식 검사가 존재 확인보다 먼저라 저장소는 호출되지 않는다
        NbrRuleSaveRequest row = createRow("C", "PROD_CD", "", 4, DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
        verify(nbrRuleRepository, never()).save(any());
    }

    @Test
    void 신규_등록_정상() {
        when(nbrRuleRepository.existsById("PROD_CD")).thenReturn(false);
        NbrRuleSaveRequest row = createRow("C", "PROD_CD", "PROD", 4, DyncKyTyp.NONE);

        nbrRuleService.saveAll(List.of(row));

        verify(nbrRuleRepository).save(any(NbrRule.class));
        verify(nbrRuleRepository).flush();
    }

    @Test
    void 수정시_dyncKyTyp을_바꾸려_하면_예외() {
        NbrRule existing = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").prfx("IB").prfxDlmt("-").deDlmt("-").seqDgt(3)
                .dyncKyTyp(DyncKyTyp.DAY)
                .build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(existing));
        NbrRuleSaveRequest row = createRow("U", "IB_NO", "IB", 3, DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
    }

    @Test
    void 수정_정상() {
        NbrRule existing = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").prfx("IB").prfxDlmt("-").deDlmt("-").seqDgt(3)
                .dyncKyTyp(DyncKyTyp.DAY)
                .build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(existing));
        NbrRuleSaveRequest row = createRow("U", "IB_NO", "IB", 4, DyncKyTyp.DAY);
        row.setPrfxDlmt("_");
        row.setDeDlmt("/");

        nbrRuleService.saveAll(List.of(row));

        assertEquals(4, existing.getSeqDgt());
        assertEquals("_", existing.getPrfxDlmt());
        assertEquals("/", existing.getDeDlmt());
    }

    @Test
    void 알수없는_status면_예외() {
        NbrRuleSaveRequest row = createRow("X", "PROD_CD", "PROD", 4, DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
    }
}
