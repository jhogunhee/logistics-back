package com.project.wmsback.strategy.inspection.component;

/** 규칙 위반 1건. actual/expected는 관리자 화면의 "왜 차단됐나" 표에 그대로 나간다 */
public record Violation(String message, String actual, String expected) {
}
