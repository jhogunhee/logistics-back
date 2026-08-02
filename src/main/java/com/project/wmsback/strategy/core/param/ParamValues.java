package com.project.wmsback.strategy.core.param;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 실행 시 구현체가 받는 파라미터 값 접근자. 저장 시 ParamValidator를 통과한 값이므로
 * 타입 위반은 정상 경로에서 발생하지 않는다 (발생 = 정의/배포 불일치).
 */
public class ParamValues {

    private final Map<String, Object> values;

    public ParamValues(Map<String, Object> values) {
        this.values = values != null ? values : Map.of();
    }

    public BigDecimal getNumber(String key, BigDecimal defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    public boolean getBool(String key, boolean defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        return value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString());
    }

    public String getString(String key, String defaultValue) {
        Object value = values.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
