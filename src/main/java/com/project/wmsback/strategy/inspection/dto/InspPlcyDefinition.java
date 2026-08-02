package com.project.wmsback.strategy.inspection.dto;

import java.util.List;
import java.util.Map;

/**
 * 검수 정책 정의 — 저장 요청 본문이자 리비전 스냅샷의 형태다 (같은 모양이라 복원이 곧 재저장).
 */
public record InspPlcyDefinition(String stgyNm, List<RuleDef> rules) {

    public record RuleDef(Integer srtSeq, String ruleCd, Map<String, Object> para) {
    }
}
