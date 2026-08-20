package com.project.wmsback.outbound.dto;

import java.util.List;

/**
 * 피킹 실행 결과. 상태가 바뀐 주문만 {@code orderChanges}에 담는다 —
 * 첫 실적의 PICKING 전이와 전 할당 소진의 PICKED 전이를 화면이 토스트로 알린다.
 */
public record PikngExecuteResponse(
        int taskCount,
        long pikngQty,
        /** 이번 실행으로 DONE(전량 피킹)이 된 지시 수 */
        int doneTaskCount,
        List<OrderChange> orderChanges
) {
    public record OrderChange(String outbNo, String status, String statusLabel) {
    }
}
