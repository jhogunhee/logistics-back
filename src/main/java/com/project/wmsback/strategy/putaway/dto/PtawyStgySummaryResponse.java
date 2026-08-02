package com.project.wmsback.strategy.putaway.dto;

import com.project.wmsback.strategy.putaway.entity.PtawyStgy;

import java.time.LocalDateTime;

/** 목록 행. odrDvsn null = 전체 — 화면이 "적용대상" 칩으로 표시한다 */
public record PtawyStgySummaryResponse(
        Long ptawyStgyId,
        String stgyNm,
        String odrDvsn,
        int stageCount,
        LocalDateTime updatedAt
) {

    public static PtawyStgySummaryResponse from(PtawyStgy stgy) {
        return new PtawyStgySummaryResponse(stgy.getId(), stgy.getStgyNm(), stgy.getOdrDvsn(),
                stgy.getStages().size(),
                stgy.getUpdatedAt() != null ? stgy.getUpdatedAt() : stgy.getCreatedAt());
    }
}
