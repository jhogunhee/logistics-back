package com.project.wmsback.strategy.core.dto;

import com.project.wmsback.strategy.core.condition.ConditionField;
import com.project.wmsback.strategy.core.condition.ConditionOperator;

import java.util.List;

/** 조건 필드 메타 (GET /strategy/meta/fields/{domain}) — ConditionBuilder가 이걸로 폼을 그린다 */
public record FieldDescriptorResponse(
        String code,
        String label,
        List<ConditionOperator> allowedOps,
        String optionSource
) {

    public static FieldDescriptorResponse from(ConditionField<?> field) {
        return new FieldDescriptorResponse(field.code(), field.label(),
                field.allowedOps().stream().sorted().toList(), field.optionSource());
    }
}
