package com.project.wmsback.strategy.core.descriptor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 구성요소가 받는 파라미터 하나의 스키마. 관리자 화면 폼과 저장 시 검증(ParamValidator)의
 * 유일한 원천이다 — 화면에 하드코딩된 선택지가 생기는 순간 P1(화면 옵션 = 구현된 실행기)이 깨진다.
 */
public record ParamSpec(
        String key,
        String label,
        ParamType type,
        boolean required,
        List<ParamOption> options,   // SELECT 계열의 정적 선택지 (동적이면 optionSource)
        String optionSource,         // 동적 선택지 소스 — GET /strategy/meta/options/{source}
        BigDecimal min,              // NUMBER 범위 검증
        BigDecimal max,
        String defaultValue
) {

    public static ParamSpec number(String key, String label, boolean required,
                                   BigDecimal min, BigDecimal max, String defaultValue) {
        return new ParamSpec(key, label, ParamType.NUMBER, required, List.of(), null, min, max, defaultValue);
    }

    public static ParamSpec bool(String key, String label, String defaultValue) {
        return new ParamSpec(key, label, ParamType.BOOLEAN, false, List.of(), null, null, null, defaultValue);
    }
}
