package com.project.wmsback.strategy.core.param;

import com.project.wmsback.strategy.core.descriptor.ParamOption;
import com.project.wmsback.strategy.core.descriptor.ParamSpec;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 파라미터 값을 ParamSpec 스키마 대비 검증·정규화한다. 저장 서비스가 호출하고,
 * 실패는 저장 거부다 — "등록은 되는데 실행하면 예외"인 상태를 만들지 않는다 (P2).
 */
public final class ParamValidator {

    private ParamValidator() {
    }

    /**
     * @return 정규화된 값 맵 (NUMBER는 BigDecimal, BOOLEAN은 Boolean으로 통일, 기본값 채움)
     * @throws IllegalArgumentException 스키마 위반 — 메시지에 구성요소·키를 명시
     */
    public static Map<String, Object> validate(String componentLabel, List<ParamSpec> specs, Map<String, Object> raw) {
        Map<String, Object> input = raw != null ? new HashMap<>(raw) : new HashMap<>();
        Map<String, Object> normalized = new LinkedHashMap<>();

        for (ParamSpec spec : specs) {
            Object value = input.remove(spec.key());
            if (value == null && spec.defaultValue() != null) {
                value = spec.defaultValue();
            }
            if (value == null) {
                if (spec.required()) {
                    throw new IllegalArgumentException(componentLabel + ": 필수 파라미터가 없습니다 — " + spec.label());
                }
                continue;
            }
            normalized.put(spec.key(), normalizeOne(componentLabel, spec, value));
        }

        if (!input.isEmpty()) {
            throw new IllegalArgumentException(componentLabel + ": 정의되지 않은 파라미터입니다 — " + input.keySet());
        }
        return normalized;
    }

    private static Object normalizeOne(String componentLabel, ParamSpec spec, Object value) {
        switch (spec.type()) {
            case NUMBER -> {
                BigDecimal number = toNumber(componentLabel, spec, value);
                if (spec.min() != null && number.compareTo(spec.min()) < 0
                        || spec.max() != null && number.compareTo(spec.max()) > 0) {
                    throw new IllegalArgumentException(componentLabel + ": " + spec.label() + " 값이 허용 범위("
                            + spec.min() + "~" + spec.max() + ")를 벗어났습니다 — " + number);
                }
                return number;
            }
            case BOOLEAN -> {
                if (value instanceof Boolean b) {
                    return b;
                }
                if (value instanceof String s && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))) {
                    return Boolean.parseBoolean(s);
                }
                throw new IllegalArgumentException(componentLabel + ": " + spec.label() + "은(는) true/false여야 합니다 — " + value);
            }
            case SELECT -> {
                String s = value.toString();
                requireInOptions(componentLabel, spec, s);
                return s;
            }
            case MULTI_SELECT -> {
                if (!(value instanceof List<?> list) || list.isEmpty()) {
                    throw new IllegalArgumentException(componentLabel + ": " + spec.label() + "은(는) 1개 이상 선택해야 합니다");
                }
                list.forEach(v -> requireInOptions(componentLabel, spec, v.toString()));
                return list.stream().map(Object::toString).toList();
            }
            default -> {
                return value.toString();
            }
        }
    }

    private static BigDecimal toNumber(String componentLabel, ParamSpec spec, Object value) {
        try {
            return value instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(componentLabel + ": " + spec.label() + "은(는) 숫자여야 합니다 — " + value);
        }
    }

    /** 정적 options가 선언된 경우에만 값 목록 검증 (optionSource 동적 선택지는 기준정보라 저장 시점 검증 대상 아님) */
    private static void requireInOptions(String componentLabel, ParamSpec spec, String value) {
        if (spec.options() == null || spec.options().isEmpty()) {
            return;
        }
        boolean known = spec.options().stream().map(ParamOption::value).anyMatch(value::equals);
        if (!known) {
            throw new IllegalArgumentException(componentLabel + ": " + spec.label() + "에 없는 선택지입니다 — " + value);
        }
    }
}
