package com.project.wmsback.strategy.wave.dto;

import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.wave.entity.WavStgy;

import java.util.List;

/** 웨이브 전략 상세 (조건그룹 포함) */
public record WavStgyResponse(
        Long wavStgyId,
        String stgyNm,
        Integer prty,
        List<List<FieldCondition>> condGrp,
        Long lastRvsnNo
) {

    public static WavStgyResponse from(WavStgy stgy) {
        return new WavStgyResponse(stgy.getId(), stgy.getStgyNm(), stgy.getPrty(),
                stgy.getCondGrp(), stgy.getLastRvsnNo());
    }

    public WavStgyDefinition toDefinition() {
        return new WavStgyDefinition(stgyNm, prty, condGrp);
    }
}
