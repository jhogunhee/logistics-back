package com.project.wmsback.master.service;

import com.project.wmsback.master.dto.NbrRuleSaveRequest;
import com.project.wmsback.master.entity.DyncKyTyp;
import com.project.wmsback.master.entity.NbrRule;
import com.project.wmsback.master.repository.NbrRuleRepository;
import com.project.wmsback.master.repository.NbrSeqRepository;
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

    private NbrRuleSaveRequest createRow(String status, String ruleCd, String ptrn, DyncKyTyp typ) {
        NbrRuleSaveRequest row = new NbrRuleSaveRequest();
        row.setStatus(status);
        row.setRuleCd(ruleCd);
        row.setRuleNm("테스트 규칙");
        row.setPtrn(ptrn);
        row.setDyncKyTyp(typ);
        row.setUsYn("Y");
        return row;
    }

    @Test
    void 신규_등록시_이미_있는_ruleCd면_예외() {
        when(nbrRuleRepository.existsById("PROD_CD")).thenReturn(true);
        NbrRuleSaveRequest row = createRow("C", "PROD_CD", "PROD-{SEQ:4}", DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
        verify(nbrRuleRepository, never()).save(any());
    }

    @Test
    void 신규_등록시_패턴이_유효하지_않으면_예외() {
        when(nbrRuleRepository.existsById("PROD_CD")).thenReturn(false);
        NbrRuleSaveRequest row = createRow("C", "PROD_CD", "PROD-0001", DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
        verify(nbrRuleRepository, never()).save(any());
    }

    @Test
    void 신규_등록_정상() {
        when(nbrRuleRepository.existsById("PROD_CD")).thenReturn(false);
        NbrRuleSaveRequest row = createRow("C", "PROD_CD", "PROD-{SEQ:4}", DyncKyTyp.NONE);

        nbrRuleService.saveAll(List.of(row));

        verify(nbrRuleRepository).save(any(NbrRule.class));
        verify(nbrRuleRepository).flush();
    }

    @Test
    void 수정시_dyncKyTyp을_바꾸려_하면_예외() {
        NbrRule existing = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").ptrn("IB-{yyyyMMdd}-{SEQ:3}").dyncKyTyp(DyncKyTyp.DATE)
                .build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(existing));
        NbrRuleSaveRequest row = createRow("U", "IB_NO", "IB-{yyyyMMdd}-{SEQ:3}", DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
    }

    @Test
    void 수정_정상() {
        NbrRule existing = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").ptrn("IB-{yyyyMMdd}-{SEQ:3}").dyncKyTyp(DyncKyTyp.DATE)
                .build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(existing));
        NbrRuleSaveRequest row = createRow("U", "IB_NO", "IB-{yyyyMMdd}-{SEQ:4}", DyncKyTyp.DATE);

        nbrRuleService.saveAll(List.of(row));

        assertEquals("IB-{yyyyMMdd}-{SEQ:4}", existing.getPtrn());
    }

    @Test
    void 알수없는_status면_예외() {
        NbrRuleSaveRequest row = createRow("X", "PROD_CD", "PROD-{SEQ:4}", DyncKyTyp.NONE);

        assertThrows(IllegalArgumentException.class, () -> nbrRuleService.saveAll(List.of(row)));
    }
}
