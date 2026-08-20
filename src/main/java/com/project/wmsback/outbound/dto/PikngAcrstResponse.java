package com.project.wmsback.outbound.dto;

import java.time.LocalDateTime;

/** 피킹 실적 1행 (실행 append-only 로그) — 실적 내역 모달용. 시각·작업자는 감사 컬럼에서 온다 */
public record PikngAcrstResponse(
        Long pikngAcrstId,
        long pikngQty,
        LocalDateTime createdAt,
        String createdBy
) {
}
