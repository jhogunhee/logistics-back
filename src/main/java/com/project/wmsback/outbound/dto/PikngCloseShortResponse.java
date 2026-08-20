package com.project.wmsback.outbound.dto;

import java.util.List;

/**
 * 결품 종결 결과. {@code shotgeQty}는 이번 종결로 <b>예약이 풀린 수량</b>과 같다 —
 * 지시 잔량 전부가 결품이고, 그만큼이 가용으로 되돌아간다.
 *
 * <p>상태가 바뀐 주문만 {@code orderChanges}에 담는 것은 피킹 실행과 같다 —
 * 마지막 결품 종결이 그 주문의 남은 할당을 소진시키면 PICKED로 전이한다.
 */
public record PikngCloseShortResponse(
        int taskCount,
        long shotgeQty,
        List<PikngExecuteResponse.OrderChange> orderChanges
) {
}
