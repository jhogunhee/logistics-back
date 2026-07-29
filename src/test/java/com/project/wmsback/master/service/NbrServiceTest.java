package com.project.wmsback.master.service;

import com.project.wmsback.master.entity.DyncKyTyp;
import com.project.wmsback.master.entity.NbrRule;
import com.project.wmsback.master.entity.NbrSeq;
import com.project.wmsback.master.repository.NbrRuleRepository;
import com.project.wmsback.master.repository.NbrSeqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NbrServiceTest {

    @Mock
    private NbrRuleRepository nbrRuleRepository;
    @Mock
    private NbrSeqRepository nbrSeqRepository;

    @InjectMocks
    private NbrService nbrService;

    @Test
    void 존재하지_않는_규칙이면_IllegalArgumentException() {
        when(nbrRuleRepository.findById("NO_SUCH")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> nbrService.issue("NO_SUCH"));
    }

    @Test
    void 비활성_규칙이면_IllegalStateException() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .usYn("N")
                .build();
        when(nbrRuleRepository.findById("PROD_CD")).thenReturn(Optional.of(rule));

        assertThrows(IllegalStateException.class, () -> nbrService.issue("PROD_CD"));
    }

    @Test
    void NONE_규칙에_날짜_오버로드를_쓰면_IllegalStateException() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();
        when(nbrRuleRepository.findById("PROD_CD")).thenReturn(Optional.of(rule));

        assertThrows(IllegalStateException.class,
                () -> nbrService.issue("PROD_CD", LocalDate.of(2026, 7, 29)));
    }

    @Test
    void DATE_규칙에_인자_없는_issue를_쓰면_IllegalStateException() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").ptrn("IB-{yyyyMMdd}-{SEQ:3}").dyncKyTyp(DyncKyTyp.DATE)
                .build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(rule));

        assertThrows(IllegalStateException.class, () -> nbrService.issue("IB_NO"));
    }

    @Test
    void NONE_규칙_기존_카운터가_있으면_그대로_증가시켜_렌더링() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();
        NbrSeq seqRow = NbrSeq.builder().ruleCd("PROD_CD").dyncKy("-").seq(6L).build();
        when(nbrRuleRepository.findById("PROD_CD")).thenReturn(Optional.of(rule));
        when(nbrSeqRepository.findByIdForUpdate("PROD_CD", "-")).thenReturn(Optional.of(seqRow));

        String number = nbrService.issue("PROD_CD");

        assertEquals("PROD-0007", number);
        verify(nbrSeqRepository, never()).insertIfAbsent(anyString(), anyString());
    }

    @Test
    void 카운터가_없으면_생성_후_재조회해_증가시킨다() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();
        NbrSeq createdRow = NbrSeq.builder().ruleCd("PROD_CD").dyncKy("-").seq(0L).build();
        when(nbrRuleRepository.findById("PROD_CD")).thenReturn(Optional.of(rule));
        when(nbrSeqRepository.findByIdForUpdate("PROD_CD", "-"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(createdRow));

        String number = nbrService.issue("PROD_CD");

        assertEquals("PROD-0001", number);
        verify(nbrSeqRepository).insertIfAbsent("PROD_CD", "-");
    }

    @Test
    void DATE_규칙은_전달받은_날짜를_동적키와_렌더링에_같이_쓴다() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("IB_NO").ruleNm("입고 번호").ptrn("IB-{yyyyMMdd}-{SEQ:3}").dyncKyTyp(DyncKyTyp.DATE)
                .build();
        NbrSeq seqRow = NbrSeq.builder().ruleCd("IB_NO").dyncKy("20260825").seq(11L).build();
        when(nbrRuleRepository.findById("IB_NO")).thenReturn(Optional.of(rule));
        when(nbrSeqRepository.findByIdForUpdate("IB_NO", "20260825")).thenReturn(Optional.of(seqRow));

        String number = nbrService.issue("IB_NO", LocalDate.of(2026, 8, 25));

        assertEquals("IB-20260825-012", number);
    }

    @Test
    void preview는_DB를_건드리지_않고_seq_1로_렌더링() {
        String number = nbrService.preview("PROD-{SEQ:4}", DyncKyTyp.NONE);

        assertEquals("PROD-0001", number);
        verifyNoInteractions(nbrRuleRepository, nbrSeqRepository);
    }

    @Test
    void preview도_패턴_검증을_통과해야_한다() {
        assertThrows(IllegalArgumentException.class,
                () -> nbrService.preview("PROD-0001", DyncKyTyp.NONE));
    }
}
