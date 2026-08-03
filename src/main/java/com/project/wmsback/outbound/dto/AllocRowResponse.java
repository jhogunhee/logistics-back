package com.project.wmsback.outbound.dto;

import java.time.LocalDate;

/**
 * 할당 레코드 1행 (라인 ↔ 재고). 해제의 단위이기도 하다.
 *
 * <p>{@code pikngQty > 0} 이면 해제할 수 없다 — 실물이 이미 나갔거나 나가는 중이라
 * 되돌리려면 역방향 이동이 필요한데 v1이 지원하지 않는다. 화면이 그 행의 해제 버튼을
 * 잠그도록 값을 함께 내린다.
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
        Long alocStgyId
) {
}
