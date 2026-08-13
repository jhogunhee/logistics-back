package com.project.wmsback.strategy.inspection.dto;

import java.util.List;

/** 실행 로그 dcsn_trc의 라인 1건 — 라인 식별 + 규칙별 판정. 모양의 주인은 이 레코드다 */
public record InspLineTrace(Long ibLineId, String prodCd, List<InspRuleResult> rules) {
}
