package com.project.wmsback.strategy.allocation.dto;

import com.project.wmsback.strategy.allocation.entity.AlocStgy;
import com.project.wmsback.strategy.allocation.entity.AllocSlotTyp;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 목록 행. 슬롯은 타입별 개수만 — 상세는 편집 화면에서 본다 */
public record AlocStgySummaryResponse(
        Long alocStgyId,
        String stgyNm,
        Integer prty,
        int tgtCondCount,
        /** 슬롯 타입 → 등록 건수. 0건인 타입은 「기본 동작」이라는 뜻이라 화면이 그렇게 표시한다 */
        Map<String, Integer> slotCounts,
        LocalDateTime updatedAt
) {

    public static AlocStgySummaryResponse from(AlocStgy stgy) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AllocSlotTyp slotTyp : AllocSlotTyp.values()) {
            counts.put(slotTyp.name(), stgy.slotsOf(slotTyp).size());
        }
        List<?> tgtCond = stgy.getTgtCond() != null ? stgy.getTgtCond() : List.of();
        return new AlocStgySummaryResponse(stgy.getId(), stgy.getStgyNm(), stgy.getPrty(),
                tgtCond.size(), counts,
                stgy.getUpdatedAt() != null ? stgy.getUpdatedAt() : stgy.getCreatedAt());
    }
}
