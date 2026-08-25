package com.project.wmsback.outbound.dto;

import java.time.LocalDate;

/**
 * 할당 레코드 1행 (라인 ↔ 재고). 해제의 단위이기도 하다.
 *
 * <p>해제가 막히는 조건 둘을 값으로 함께 내려 화면이 그 행을 체크 단계에서 잠근다 —
 * {@code pikngQty > 0}(실물이 이미 나갔거나 나가는 중이라 되돌리려면 역방향 이동이
 * 필요한데 v1이 지원하지 않는다)과 {@code hasLiveTask}(지시가 삭제된 할당을 가리키는
 * 미아가 되므로 그 지시의 취소가 먼저다). 서버 해제 가드와 같은 판정이다.
 */
public record AllocRowResponse(
        Long outbAllocId,
        Long outbLineId,
        Long invId,
        Long locId,
        String locCd,
        Long lotId,
        String lotNo,
        LocalDate expiryDt,
        long alocQty,
        long pikngQty,
        /**
         * 이 할당을 만든 전략. NULL = 수동할당 또는 전략 미설정 기간의 기본 동작 —
         * 「전략 없이 만들어짐」이 두 경우를 한 뜻으로 덮으므로 화면도 한 가지로 표시한다.
         */
        Long alocStgyId,
        /** 살아 있는 피킹지시가 붙어 있다(취소된 지시는 세지 않는다) — 해제하려면 그 지시의 취소가 먼저다 */
        boolean hasLiveTask
) {
}
