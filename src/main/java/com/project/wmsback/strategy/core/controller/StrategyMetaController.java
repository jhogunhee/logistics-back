package com.project.wmsback.strategy.core.controller;

import com.project.wmsback.strategy.core.dto.ComponentResponse;
import com.project.wmsback.strategy.core.dto.FieldDescriptorResponse;
import com.project.wmsback.strategy.core.dto.OptionResponse;
import com.project.wmsback.strategy.core.service.StrategyOptionService;
import com.project.wmsback.strategy.inspection.rule.InspectionRule;
import com.project.wmsback.strategy.putaway.field.PutawayLocField;
import com.project.wmsback.strategy.putaway.field.PutawayTargetField;
import com.project.wmsback.strategy.putaway.method.PutawayMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 전략 편집 화면의 메타데이터. 프론트는 이 API들만으로 모든 전략 편집 폼을 렌더링한다 —
 * 폼에 하드코딩된 선택지가 생기는 순간 P1(화면 옵션 = 구현된 실행기)이 깨진다.
 * 선택지의 원천은 각 도메인의 enum(InspectionRule·PutawayMethod·필드 enum)이다.
 */
@RestController
@RequestMapping("/strategy/meta")
@RequiredArgsConstructor
public class StrategyMetaController {

    private final StrategyOptionService optionService;

    @GetMapping("/inspection-rules")
    public List<ComponentResponse> inspectionRules() {
        return Arrays.stream(InspectionRule.values())
                .map(r -> new ComponentResponse(r.name(), r.label(), r.dscr(), r.deprecated()))
                .toList();
    }

    @GetMapping("/putaway-methods")
    public List<ComponentResponse> putawayMethods() {
        return Arrays.stream(PutawayMethod.values())
                .map(m -> new ComponentResponse(m.name(), m.label(), m.dscr(), m.deprecated()))
                .toList();
    }

    @GetMapping("/fields/{domain}")
    public List<FieldDescriptorResponse> fields(@PathVariable String domain) {
        return switch (domain) {
            case "putaway-target" -> Arrays.stream(PutawayTargetField.values())
                    .map(FieldDescriptorResponse::from).toList();
            case "putaway-loc" -> Arrays.stream(PutawayLocField.values())
                    .map(FieldDescriptorResponse::from).toList();
            default -> throw new IllegalArgumentException("없는 조건 필드 도메인입니다: " + domain);
        };
    }

    @GetMapping("/options/{source}")
    public List<OptionResponse> options(@PathVariable String source) {
        return optionService.options(source);
    }
}
