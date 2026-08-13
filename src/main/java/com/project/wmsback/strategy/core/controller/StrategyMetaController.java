package com.project.wmsback.strategy.core.controller;

import com.project.wmsback.strategy.allocation.component.AlocDstrb;
import com.project.wmsback.strategy.allocation.component.AlocRstrct;
import com.project.wmsback.strategy.allocation.component.AlocSrt;
import com.project.wmsback.strategy.allocation.entity.AlocSlotTyp;
import com.project.wmsback.strategy.allocation.field.AlocInvnField;
import com.project.wmsback.strategy.allocation.field.AlocLineField;
import com.project.wmsback.strategy.allocation.field.AlocTgtField;
import com.project.wmsback.strategy.allocation.field.InvnSortField;
import com.project.wmsback.strategy.allocation.field.OdrSortField;
import com.project.wmsback.strategy.core.dto.ComponentResponse;
import com.project.wmsback.strategy.core.dto.FieldDescriptorResponse;
import com.project.wmsback.strategy.core.dto.OptionResponse;
import com.project.wmsback.strategy.core.service.StrategyOptionService;
import com.project.wmsback.strategy.inspection.component.InspectionRule;
import com.project.wmsback.strategy.putaway.field.PutawayLocField;
import com.project.wmsback.strategy.putaway.field.PutawaySortField;
import com.project.wmsback.strategy.putaway.field.PutawayTargetField;
import com.project.wmsback.strategy.putaway.component.PutawayMethod;
import com.project.wmsback.strategy.wave.field.WaveOrderField;
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

    /**
     * 할당 슬롯의 구현체 목록. 슬롯 타입마다 구현체 집합이 다르므로 경로에 타입을 받는다 —
     * 재고위치(INVN_FLTR)는 구현체 축이 없어 빈 목록이고, 화면은 그 섹션에서 피커를 감춘다.
     */
    @GetMapping("/allocation-components/{slotTyp}")
    public List<ComponentResponse> allocationComponents(@PathVariable String slotTyp) {
        AlocSlotTyp typ;
        try {
            typ = AlocSlotTyp.valueOf(slotTyp);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("없는 할당 슬롯 타입입니다: " + slotTyp);
        }
        return switch (typ) {
            case INVN_FLTR -> List.of();
            case RSTRCT -> Arrays.stream(AlocRstrct.values())
                    .map(r -> new ComponentResponse(r.name(), r.label(), r.dscr(), r.deprecated())).toList();
            case INVN_SRT, ODR_SRT -> Arrays.stream(AlocSrt.values())
                    .map(s -> new ComponentResponse(s.name(), s.label(), s.dscr(), s.deprecated())).toList();
            case DSTRB -> Arrays.stream(AlocDstrb.values())
                    .map(d -> new ComponentResponse(d.name(), d.label(), d.dscr(), d.deprecated())).toList();
        };
    }

    /** 정렬 기준 목록 (MULTI_SORT의 para.criteria와 적치 loc_srt가 고를 수 있는 field) */
    @GetMapping("/sort-fields/{domain}")
    public List<OptionResponse> sortFields(@PathVariable String domain) {
        return switch (domain) {
            case "allocation-invn" -> Arrays.stream(InvnSortField.values())
                    .map(f -> new OptionResponse(f.name(), f.label())).toList();
            case "allocation-order" -> Arrays.stream(OdrSortField.values())
                    .map(f -> new OptionResponse(f.name(), f.label())).toList();
            case "putaway-loc" -> Arrays.stream(PutawaySortField.values())
                    .map(f -> new OptionResponse(f.name(), f.label())).toList();
            default -> throw new IllegalArgumentException("없는 정렬 기준 도메인입니다: " + domain);
        };
    }

    @GetMapping("/fields/{domain}")
    public List<FieldDescriptorResponse> fields(@PathVariable String domain) {
        return switch (domain) {
            case "putaway-target" -> Arrays.stream(PutawayTargetField.values())
                    .map(FieldDescriptorResponse::from).toList();
            case "putaway-loc" -> Arrays.stream(PutawayLocField.values())
                    .map(FieldDescriptorResponse::from).toList();
            case "wave-order" -> Arrays.stream(WaveOrderField.values())
                    .map(FieldDescriptorResponse::from).toList();
            case "allocation-target" -> Arrays.stream(AlocTgtField.values())
                    .map(FieldDescriptorResponse::from).toList();
            case "allocation-invn" -> Arrays.stream(AlocInvnField.values())
                    .map(FieldDescriptorResponse::from).toList();
            case "allocation-line" -> Arrays.stream(AlocLineField.values())
                    .map(FieldDescriptorResponse::from).toList();
            default -> throw new IllegalArgumentException("없는 조건 필드 도메인입니다: " + domain);
        };
    }

    @GetMapping("/options/{source}")
    public List<OptionResponse> options(@PathVariable String source) {
        return optionService.options(source);
    }
}
