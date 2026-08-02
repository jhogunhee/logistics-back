package com.project.wmsback.strategy.putaway.dto;

import java.time.LocalDateTime;

/**
 * 삭제된 적치 전략 1건 — stgy_rvsn에만 남은 전략. 이름은 마지막 스냅샷에서 추출한다.
 * 복원 진입점: POST /strategy/putaway-strategies/{stgyId}/revisions/{lastRvsnNo}/restore (새 전략 생성).
 */
public record PtawyStgyDeletedResponse(
        Long stgyId,
        String stgyNm,
        Long lastRvsnNo,
        LocalDateTime lastSavedAt
) {
}
