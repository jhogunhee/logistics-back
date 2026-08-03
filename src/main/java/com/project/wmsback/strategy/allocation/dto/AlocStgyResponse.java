package com.project.wmsback.strategy.allocation.dto;

import com.project.wmsback.strategy.allocation.entity.AlocStgy;
import com.project.wmsback.strategy.allocation.entity.AlocStgySlot;
import com.project.wmsback.strategy.core.condition.FieldCondition;

import java.time.LocalDateTime;
import java.util.List;

/** 할당 전략 상세 응답. {@link #toDefinition()}으로 미리보기·리비전 스냅샷과 같은 형태가 된다 */
public record AlocStgyResponse(
        Long alocStgyId,
        String stgyNm,
        Integer prty,
        List<FieldCondition> tgtCond,
        List<AlocStgyDefinition.SlotDef> slots,
        Long lastRvsnNo,
        LocalDateTime updatedAt
) {

    public static AlocStgyResponse from(AlocStgy stgy) {
        List<AlocStgyDefinition.SlotDef> slots = stgy.getSlots().stream()
                .map(AlocStgyResponse::toSlotDef)
                .toList();
        return new AlocStgyResponse(stgy.getId(), stgy.getStgyNm(), stgy.getPrty(),
                stgy.getTgtCond(), slots, stgy.getLastRvsnNo(),
                stgy.getUpdatedAt() != null ? stgy.getUpdatedAt() : stgy.getCreatedAt());
    }

    private static AlocStgyDefinition.SlotDef toSlotDef(AlocStgySlot slot) {
        return new AlocStgyDefinition.SlotDef(slot.getSlotTyp(), slot.getSrtSeq(),
                slot.getCmpntCd(), slot.getPara(), slot.getCond());
    }

    public AlocStgyDefinition toDefinition() {
        return new AlocStgyDefinition(stgyNm, prty, tgtCond, slots);
    }
}
