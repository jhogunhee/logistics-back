package com.project.wmsback.strategy.inspection.dto;

import java.util.List;

/** 미리보기 응답 — 로트별 × 규칙별 판정. PreviewPanel이 그대로 표를 그린다 */
public record InspPreviewResponse(List<LotResult> lots) {

    public record LotResult(Long prodId, String prodCd, String prodNm, List<InspRuleResult> rules) {
    }
}
