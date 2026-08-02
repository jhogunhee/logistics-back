package com.project.wmsback.strategy.inspection.dto;

/** 규칙 1건의 판정 결과 — 미리보기 응답과 실행 로그 trace가 같은 모양을 쓴다 */
public record InspRuleResult(
        String ruleCd,
        String ruleName,
        boolean pass,
        String skipReason,   // null 아니면 관리 대상 아님 (pass=true로 간주)
        String message,      // 위반 시 사유
        String actual,
        String expected
) {

    public static InspRuleResult pass(String ruleCd, String ruleName) {
        return new InspRuleResult(ruleCd, ruleName, true, null, null, null, null);
    }

    public static InspRuleResult skip(String ruleCd, String ruleName, String reason) {
        return new InspRuleResult(ruleCd, ruleName, true, reason, null, null, null);
    }

    public static InspRuleResult violation(String ruleCd, String ruleName, String message, String actual, String expected) {
        return new InspRuleResult(ruleCd, ruleName, false, null, message, actual, expected);
    }
}
