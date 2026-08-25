package com.project.omsback.inbound.service;

import com.project.common.batch.BatchResult;
import com.project.omsback.inbound.dto.AtoOdrIssueRequest;
import com.project.omsback.inbound.dto.AtoOdrProposalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 자동발주 스케줄러 — 산정 결과를 그대로 발행에 싣는 얇은 접착부.
 * 검증·수량 규칙은 plan/issue가 갖고 있으므로 여기서는 「무엇을 언제 부르나」만 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class AtoOdrSchedulerTest {

    @Mock AtoOdrService atoOdrService;
    @InjectMocks AtoOdrScheduler scheduler;

    private AtoOdrProposalResponse proposal(long vendorId, LocalDate expctDe,
                                            List<AtoOdrProposalResponse.Line> lines) {
        return new AtoOdrProposalResponse(vendorId, "VD-000" + vendorId, "거래처", expctDe, lines);
    }

    private AtoOdrProposalResponse.Line line(long prodId, long odrQty) {
        return new AtoOdrProposalResponse.Line(prodId * 10, prodId, "PROD-" + prodId, "상품",
                "BOX", 24, 0, 0, 0, 0, 0, 100, 240, 240, 1, 2, odrQty);
    }

    @Test
    @DisplayName("벤더별 제안을 (상품, 수량) 그대로 발행 요청으로 옮긴다 — 벤더 1곳 = 요청 1건")
    void issuesProposalsPerVendor() {
        LocalDate expctDe = LocalDate.of(2026, 8, 27);
        when(atoOdrService.plan(any())).thenReturn(List.of(
                proposal(1, expctDe, List.of(line(10, 5), line(11, 2))),
                proposal(2, expctDe, List.of(line(12, 3)))
        ));
        when(atoOdrService.issueAll(anyList())).thenReturn(new BatchResult(List.of(1L, 2L), List.of()));

        scheduler.issueDaily();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AtoOdrIssueRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(atoOdrService).issueAll(captor.capture());
        List<AtoOdrIssueRequest> requests = captor.getValue();
        assertEquals(2, requests.size());
        assertEquals(1L, requests.get(0).getVendorId());
        assertEquals(expctDe, requests.get(0).getExpctDe());
        assertEquals(2, requests.get(0).getItems().size());
        assertEquals(10L, requests.get(0).getItems().get(0).getProdId());
        assertEquals(5L, requests.get(0).getItems().get(0).getOdrQty());
        assertEquals(1, requests.get(1).getItems().size());
    }

    @Test
    @DisplayName("제안이 없으면 발행을 부르지 않는다")
    void skipsIssueWhenNothingShort() {
        when(atoOdrService.plan(any())).thenReturn(List.of());

        scheduler.issueDaily();

        verify(atoOdrService, never()).issueAll(anyList());
    }

    @Test
    @DisplayName("일부 벤더가 실패해도 예외를 밖으로 던지지 않는다 — 스케줄은 계속되고 다음 주기가 다시 잡는다")
    void keepsGoingWhenSomeVendorsFail() {
        when(atoOdrService.plan(any())).thenReturn(List.of(proposal(1, LocalDate.now(), List.of(line(10, 5)))));
        when(atoOdrService.issueAll(anyList())).thenReturn(
                new BatchResult(List.of(), List.of(new BatchResult.Failure(1L, "거래처 없음"))));

        assertDoesNotThrow(scheduler::issueDaily);
    }

    @Test
    @DisplayName("산정이 실패해도 예외를 삼킨다 — 스케줄러가 죽으면 다음 주기도 돌지 않는다")
    void swallowsPlanFailure() {
        when(atoOdrService.plan(any())).thenThrow(new IllegalStateException("DB 연결 끊김"));

        assertDoesNotThrow(scheduler::issueDaily);
        verify(atoOdrService, never()).issueAll(anyList());
    }
}
