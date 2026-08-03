package com.project.wmsback.strategy.core.dto;

/**
 * 전략 구성요소(검수 규칙·적치 방식) 화면 선택지 (GET /strategy/meta/…).
 * code는 DB에 저장되는 enum name — 목록은 코드(enum)에서만 나온다 (P1).
 */
public record ComponentResponse(
        String code,
        String name,
        String dscr,
        boolean deprecated
) {
}
