package com.project.wmsback.strategy.putaway.dto;

import com.project.wmsback.strategy.core.condition.SortCriterion;
import com.project.wmsback.strategy.putaway.entity.PtawyStgy;

import java.util.List;

/** 적치 전략 상세 (단계 포함) */
public record PtawyStgyResponse(
        Long ptawyStgyId,
        String stgyNm,
        String odrDvsn,
        Boolean untSpltYn,
        List<SortCriterion> locSrt,
        List<PtawyStgyDefinition.StageDef> stages,
        Long lastRvsnNo
) {

    public static PtawyStgyResponse from(PtawyStgy stgy) {
        return new PtawyStgyResponse(stgy.getId(), stgy.getStgyNm(), stgy.getOdrDvsn(), stgy.getUntSpltYn(),
                stgy.getLocSrt(),
                stgy.getStages().stream()
                        .map(s -> new PtawyStgyDefinition.StageDef(s.getSrtSeq(), s.getMthdCd(),
                                s.getMthdPara(), s.getLineCond(), s.getLocCond()))
                        .toList(),
                stgy.getLastRvsnNo());
    }

    public PtawyStgyDefinition toDefinition() {
        return new PtawyStgyDefinition(stgyNm, odrDvsn, untSpltYn, locSrt, stages);
    }
}
