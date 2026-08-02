package com.project.wmsback.strategy.inspection.dto;

import com.project.wmsback.strategy.inspection.entity.InspPlcy;

import java.util.List;

/** 검수 정책 단건 응답. 정책 미존재 시 exists=false — 화면은 "정책 만들기" 빈 상태를 그린다 */
public record InspPlcyResponse(
        boolean exists,
        Long inspPlcyId,
        String stgyNm,
        Long lastRvsnNo,
        List<InspPlcyDefinition.RuleDef> rules
) {

    public static InspPlcyResponse from(InspPlcy plcy) {
        return new InspPlcyResponse(true, plcy.getId(), plcy.getStgyNm(), plcy.getLastRvsnNo(),
                plcy.getRules().stream()
                        .map(r -> new InspPlcyDefinition.RuleDef(r.getSrtSeq(), r.getRuleCd(), r.getPara()))
                        .toList());
    }

    public static InspPlcyResponse empty() {
        return new InspPlcyResponse(false, null, null, null, List.of());
    }
}
