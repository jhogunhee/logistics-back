package com.project.wmsback.strategy.wave.dto;

import com.project.wmsback.strategy.wave.entity.WavStgy;

import java.time.LocalDateTime;
import java.util.List;

/** 목록 행. 조건은 개수만 — 상세는 편집 화면에서 본다 */
public record WavStgySummaryResponse(
        Long wavStgyId,
        String stgyNm,
        Integer prty,
        int grpCount,
        int condCount,
        LocalDateTime updatedAt
) {

    public static WavStgySummaryResponse from(WavStgy stgy) {
        List<?> groups = stgy.getCondGrp() != null ? stgy.getCondGrp() : List.of();
        int conds = stgy.getCondGrp() == null ? 0
                : stgy.getCondGrp().stream().mapToInt(List::size).sum();
        return new WavStgySummaryResponse(stgy.getId(), stgy.getStgyNm(), stgy.getPrty(),
                groups.size(), conds,
                stgy.getUpdatedAt() != null ? stgy.getUpdatedAt() : stgy.getCreatedAt());
    }
}
