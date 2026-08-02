package com.project.wmsback.strategy.putaway.dto;

import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.putaway.entity.PtawyStgy;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 목록 행. tgtCond를 그대로 실어 화면이 "적용대상 요약"을 그린다 —
 * 이름이 아니라 조건이 항상 병기되는 것이 레거시 "이름/조건 불일치" 문제의 화면 대응이다.
 */
public record PtawyStgySummaryResponse(
        Long ptawyStgyId,
        String stgyNm,
        Integer prty,
        List<FieldCondition> tgtCond,
        int stageCount,
        LocalDateTime updatedAt
) {

    public static PtawyStgySummaryResponse from(PtawyStgy stgy) {
        return new PtawyStgySummaryResponse(stgy.getId(), stgy.getStgyNm(), stgy.getPrty(),
                stgy.getTgtCond(), stgy.getStages().size(),
                stgy.getUpdatedAt() != null ? stgy.getUpdatedAt() : stgy.getCreatedAt());
    }
}
