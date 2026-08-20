package com.project.wmsback.outbound.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 할당 대상 웨이브 1행. 수량 셋은 이 웨이브에 편성된 전 라인의 합계다.
 *
 * <p>{@code remainQty > 0} 인 웨이브만 목록에 오른다. 라인별로 과할당이 막혀 있어
 * ({@code SUM(aloc_qty) <= odr_qty}) 웨이브 합계의 잔량과 「잔량 있는 라인의 존재」가
 * 정확히 같은 뜻이 된다 — 그래서 라인 단위 EXISTS 대신 합계로 판정할 수 있다.
 */
public record AllocWaveResponse(
        Long wavId,
        String wavNo,
        Long wavStgyId,
        /** 웨이브의 출고예정일 — 편성 가드가 소속 주문의 출고예정일을 하나로 강제하므로 어느 주문의 값이든 같다 */
        LocalDate expctDe,
        LocalDateTime createdAt,
        long orderCount,
        long odrQty,
        long alocQty,
        long remainQty
) {
    public static AllocWaveResponse of(Long wavId, String wavNo, Long wavStgyId, LocalDate expctDe,
                                       LocalDateTime createdAt, long orderCount, long odrQty, long alocQty) {
        return new AllocWaveResponse(wavId, wavNo, wavStgyId, expctDe, createdAt,
                orderCount, odrQty, alocQty, odrQty - alocQty);
    }
}
