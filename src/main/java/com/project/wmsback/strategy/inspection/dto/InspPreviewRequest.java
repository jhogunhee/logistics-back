package com.project.wmsback.strategy.inspection.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 검수 정책 미리보기 요청. definition은 화면의 미저장 상태 그대로 —
 * "저장하면 곧 운영 반영"이라는 긴장을 미리보기 워크플로가 흡수한다 (P4).
 */
public record InspPreviewRequest(InspPlcyDefinition definition, List<PreviewLot> lots) {

    /** 판정 대상 로트 (실존 검수 대기 라인 또는 가상 입력 — 서버는 구분하지 않는다) */
    public record PreviewLot(Long prodId, LocalDate mfgDt, LocalDate receiptDt) {
    }
}
