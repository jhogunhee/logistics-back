package com.project.wmsback.strategy.putaway.dto;

import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.condition.SortCriterion;

import java.util.List;
import java.util.Map;

/** 적치 전략 정의 — 저장 요청 본문이자 리비전 스냅샷의 형태. odrDvsn null = 전체 적용 */
public record PtawyStgyDefinition(
        String stgyNm,
        String odrDvsn,
        Boolean untSpltYn,
        List<SortCriterion> locSrt,
        List<StageDef> stages
) {

    /**
     * 단계 1개. lineCond = 조건("이 조건일 때만 이 단계 적용"),
     * locCond = 적치위치 지정(BIZ_DVSN IN 최대 1건 — 조건이 아니라 적용기준값).
     */
    public record StageDef(
            Integer srtSeq,
            String mthdCd,
            Map<String, Object> mthdPara,
            List<FieldCondition> lineCond,
            List<FieldCondition> locCond
    ) {
    }
}
