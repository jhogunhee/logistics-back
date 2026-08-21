package com.project.wmsback.outbound.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 출고확정 화면의 웨이브 목록 행 — ISSUED 웨이브와 소속 주문의 상태별 건수.
 *
 * @param readyCount   확정대상 = PICKED(정상) + CREATED(할당 0건, 전량 미출고). <b>0이 아니면 화면이 강조한다</b>
 * @param workingCount 작업중 = ALLOCATED + PICKING — 이 주문이 섞이면 그 주문은 확정할 수 없다
 * @param shippedCount 확정완료 = SHIPPED. orderCount와 같아지는 순간 웨이브가 CLOSED가 된다
 */
public record ShmtWaveResponse(
        Long wavId,
        String wavNo,
        LocalDate expctDe,
        LocalDateTime issuedDt,
        long orderCount,
        long readyCount,
        long workingCount,
        long shippedCount
) {
}
