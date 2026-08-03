package com.project.wmsback.strategy.core.condition;

import java.util.List;

/**
 * 조건 1건 — {필드, 연산자, 값들}. JSONB 조건 배열(line_cond·loc_cond)의 원소이며
 * 배열 원소 간은 AND다.
 */
public record FieldCondition(String fld, ConditionOperator op, List<String> vals) {
}
