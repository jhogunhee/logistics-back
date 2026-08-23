package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.SpmtIssueRequest;
import com.project.wmsback.inventory.dto.SpmtTargetResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.project.mdm.prod.entity.TmpZon.DRY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 정기 보충 스케줄러 — 산정 결과의 추천 배정을 그대로 발행하는 얇은 접착부.
 * 검증·예약 규칙은 plan/issue가 갖고 있으므로 여기서는 「무엇을 언제 부르나」만 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class SpmtSchedulerTest {

    @Mock SpmtService spmtService;
    @InjectMocks SpmtScheduler scheduler;

    private SpmtTargetResponse target(long locId, List<SpmtTargetResponse.Assignment> assignments) {
        return new SpmtTargetResponse(locId * 10, locId, "P-0" + locId, "PIKNG", 10L, "PROD-10", "상품",
                DRY, 20, 100, 5, 0, 95, assignments, List.of());
    }

    @Test
    @DisplayName("추천 배정을 (원천, 대상, 수량) 그대로 발행에 싣는다")
    void issuesPlannedAssignments() {
        when(spmtService.plan(any())).thenReturn(List.of(
                target(1, List.of(
                        new SpmtTargetResponse.Assignment(101L, "S-01", "LOT-1", null, 60, 60),
                        new SpmtTargetResponse.Assignment(102L, "S-02", "LOT-2", null, 60, 35))),
                target(2, List.of(
                        new SpmtTargetResponse.Assignment(102L, "S-02", "LOT-2", null, 60, 25)))
        ));
        when(spmtService.issue(any())).thenReturn(List.of("SP-1", "SP-2", "SP-3"));

        scheduler.issueDaily();

        ArgumentCaptor<SpmtIssueRequest> captor = ArgumentCaptor.forClass(SpmtIssueRequest.class);
        verify(spmtService).issue(captor.capture());
        List<SpmtIssueRequest.Item> items = captor.getValue().getItems();
        assertEquals(3, items.size());
        assertEquals(101L, items.get(0).getInvId());
        assertEquals(1L, items.get(0).getToLocId());
        assertEquals(60L, items.get(0).getQty());
        assertEquals(2L, items.get(2).getToLocId());
        assertEquals(25L, items.get(2).getQty());
    }

    @Test
    @DisplayName("배정할 것이 없으면 발행을 부르지 않는다 — 대상이 있어도 원천이 없으면 마찬가지")
    void skipsIssueWhenNothingAssigned() {
        when(spmtService.plan(any())).thenReturn(List.of(target(1, List.of())));

        scheduler.issueDaily();

        verify(spmtService, never()).issue(any());
    }

    @Test
    @DisplayName("실패해도 예외를 밖으로 던지지 않는다 — 로그만 남기고 다음 주기가 다시 시도한다")
    void swallowsFailure() {
        when(spmtService.plan(any())).thenThrow(new IllegalStateException("DB down"));

        assertDoesNotThrow(() -> scheduler.issueDaily());
    }
}
