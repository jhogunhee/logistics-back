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
import static org.mockito.Mockito.times;
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
    @DisplayName("추천 배정을 (원천, 대상, 수량) 그대로 발행에 싣되, 대상(고정로케이션)마다 따로 발행한다")
    void issuesPlannedAssignmentsPerTarget() {
        when(spmtService.plan(any())).thenReturn(List.of(
                target(1, List.of(
                        new SpmtTargetResponse.Assignment(101L, "S-01", "LOT-1", null, 60, 60),
                        new SpmtTargetResponse.Assignment(102L, "S-02", "LOT-2", null, 60, 35))),
                target(2, List.of(
                        new SpmtTargetResponse.Assignment(102L, "S-02", "LOT-2", null, 60, 25)))
        ));
        when(spmtService.issue(any())).thenReturn(List.of("SP-1"));

        scheduler.issueDaily();

        ArgumentCaptor<SpmtIssueRequest> captor = ArgumentCaptor.forClass(SpmtIssueRequest.class);
        verify(spmtService, times(2)).issue(captor.capture());
        List<SpmtIssueRequest.Item> first = captor.getAllValues().get(0).getItems();
        assertEquals(2, first.size());
        assertEquals(101L, first.get(0).getInvId());
        assertEquals(1L, first.get(0).getToLocId());
        assertEquals(60L, first.get(0).getQty());
        List<SpmtIssueRequest.Item> second = captor.getAllValues().get(1).getItems();
        assertEquals(1, second.size());
        assertEquals(2L, second.get(0).getToLocId());
        assertEquals(25L, second.get(0).getQty());
    }

    @Test
    @DisplayName("한 대상의 발행이 실패해도 나머지 대상은 계속 발행한다 — 무인 경로라 한 행이 창고 전체를 막으면 안 된다")
    void continuesAfterOneTargetFails() {
        when(spmtService.plan(any())).thenReturn(List.of(
                target(1, List.of(new SpmtTargetResponse.Assignment(101L, "S-01", "LOT-1", null, 60, 60))),
                target(2, List.of(new SpmtTargetResponse.Assignment(102L, "S-02", "LOT-2", null, 60, 25)))
        ));
        when(spmtService.issue(any()))
                .thenThrow(new IllegalArgumentException("도착 로케이션의 적재가능수량을 초과했습니다"))
                .thenReturn(List.of("SP-2"));

        assertDoesNotThrow(() -> scheduler.issueDaily());

        verify(spmtService, times(2)).issue(any());
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
