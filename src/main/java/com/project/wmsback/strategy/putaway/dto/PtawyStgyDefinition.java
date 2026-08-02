package com.project.wmsback.strategy.putaway.dto;

import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.condition.SortCriterion;

import java.util.List;
import java.util.Map;

/** 적치 전략 정의 — 저장 요청 본문이자 리비전 스냅샷의 형태 (같은 모양이라 복원 = 재저장) */
public record PtawyStgyDefinition(
        String stgyNm,
        Integer prty,
        Boolean untSpltYn,
        List<FieldCondition> tgtCond,
        List<SortCriterion> locSrt,
        List<StageDef> stages
) {

    public record StageDef(
            Integer srtSeq,
            String mthdCd,
            Map<String, Object> mthdPara,
            List<FieldCondition> lineCond,
            List<FieldCondition> locCond
    ) {
    }
}
