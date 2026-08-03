package com.project.wmsback.strategy.allocation.service;

import com.project.wmsback.strategy.allocation.component.AlocDstrb;
import com.project.wmsback.strategy.allocation.component.AlocRstrct;
import com.project.wmsback.strategy.allocation.component.AlocSrt;
import com.project.wmsback.strategy.allocation.dto.AlocStgyDefinition;
import com.project.wmsback.strategy.allocation.entity.AllocSlotTyp;
import com.project.wmsback.strategy.allocation.field.InvnSortField;
import com.project.wmsback.strategy.core.condition.ConditionOperator;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 저장 검증 (P2). DB CHECK가 볼 수 없는 것 — <b>행 개수와 행 사이의 관계</b> — 이 여기 몫이라
 * 이 규칙들이 깨지면 「저장은 되는데 의도가 성립하지 않는」 정의가 운영에 나간다.
 *
 * <p>검증은 저장소를 건드리지 않는 순수 판정이라 협력자를 null로 두고 부른다.
 */
class AlocStgyServiceValidateTest {

    private final AlocStgyService service = new AlocStgyService(null, null);

    @Test
    @DisplayName("슬롯이 하나도 없어도 저장된다 — 전략은 기본 동작을 덮어쓰는 것이라 필수 슬롯이 없다")
    void emptySlotsAreValid() {
        assertDoesNotThrow(() -> service.validate(def(List.of())));
    }

    @Test
    @DisplayName("적용대상이 비어도 저장된다 — 전체 매칭 폴백 전략이다")
    void emptyTargetIsValid() {
        AlocStgyDefinition result = service.validate(
                new AlocStgyDefinition("폴백", 0, List.of(), List.of()));
        assertEquals(0, result.tgtCond().size());
    }

    @Test
    @DisplayName("마지막 분배에 조건이 있으면 거부 — 어느 조건에도 안 걸린 라인이 0을 받는다")
    void lastDistributionMustBeOpen() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.validate(def(List.of(
                        dstrb(AlocDstrb.SEQUENTIAL, storeCond())))));
        assertEquals(true, e.getMessage().contains("마지막 분배"));
    }

    @Test
    @DisplayName("같은 분배 방식을 조건만 다르게 두 번 쓰는 것은 정상 — 우선 배분의 기본형이다")
    void sameDistributionTwiceIsValid() {
        assertDoesNotThrow(() -> service.validate(def(List.of(
                dstrb(AlocDstrb.SEQUENTIAL, storeCond()),
                dstrb(AlocDstrb.SEQUENTIAL, List.of())))));
    }

    @Test
    @DisplayName("같은 제약을 두 번 등록하면 거부 — AND라 뒤엣것이 아무 일도 하지 않는다")
    void duplicateRestrictionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(def(List.of(
                rstrct(), rstrct()))));
    }

    @Test
    @DisplayName("조건 없는 계층은 마지막에만 — 앞에 두면 후보 전체를 가져가 뒤 계층이 죽는다")
    void openTierMustBeLast() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(def(List.of(
                tier(List.of()), tier(bizDvsnCond())))));

        assertDoesNotThrow(() -> service.validate(def(List.of(
                tier(bizDvsnCond()), tier(List.of())))));
    }

    @Test
    @DisplayName("단일 슬롯을 2건 등록하면 거부")
    void singleSlotRejectsSecond() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(def(List.of(
                multiSort(InvnSortField.EXPIRY_DT), multiSort(InvnSortField.MFG_DT)))));
    }

    @Test
    @DisplayName("정렬 기준이 비면 거부 — 비워두려면 슬롯을 지워야 기본값(FEFO)이 산다")
    void sortNeedsCriteria() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(def(List.of(
                new AlocStgyDefinition.SlotDef(AllocSlotTyp.INVN_SRT, 1,
                        AlocSrt.MULTI_SORT.name(), Map.of(), List.of())))));
    }

    @Test
    @DisplayName("srt_seq는 화면 순서대로 1..n으로 정규화된다")
    void srtSeqIsNormalized() {
        AlocStgyDefinition result = service.validate(def(List.of(
                dstrb(AlocDstrb.RATIO, storeCond()),
                dstrb(AlocDstrb.SEQUENTIAL, List.of()))));

        List<AlocStgyDefinition.SlotDef> slots = result.slotsOf(AllocSlotTyp.DSTRB);
        assertEquals(1, slots.get(0).srtSeq());
        assertEquals(2, slots.get(1).srtSeq());
    }

    @Test
    @DisplayName("재고위치 슬롯에 구현체를 보내면 거부 — 이 슬롯은 조건이 곧 정의다")
    void filterSlotRejectsComponent() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(def(List.of(
                new AlocStgyDefinition.SlotDef(AllocSlotTyp.INVN_FLTR, 1, "ANYTHING",
                        Map.of(), bizDvsnCond())))));
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────────

    private AlocStgyDefinition def(List<AlocStgyDefinition.SlotDef> slots) {
        return new AlocStgyDefinition("테스트", 0, List.of(), slots);
    }

    private List<FieldCondition> storeCond() {
        return List.of(new FieldCondition("STORE_CD", ConditionOperator.IN, List.of("ST-0001")));
    }

    private List<FieldCondition> bizDvsnCond() {
        return List.of(new FieldCondition("BIZ_DVSN", ConditionOperator.IN, List.of("PIKNG")));
    }

    private AlocStgyDefinition.SlotDef dstrb(AlocDstrb cmpnt, List<FieldCondition> cond) {
        return new AlocStgyDefinition.SlotDef(AllocSlotTyp.DSTRB, 1, cmpnt.name(), Map.of(), cond);
    }

    private AlocStgyDefinition.SlotDef rstrct() {
        return new AlocStgyDefinition.SlotDef(AllocSlotTyp.RSTRCT, 1,
                AlocRstrct.SHELF_LIFE_PCT.name(), Map.of(AlocRstrct.PARA_BASIS, AlocRstrct.BASIS_STORE),
                List.of());
    }

    private AlocStgyDefinition.SlotDef tier(List<FieldCondition> cond) {
        return new AlocStgyDefinition.SlotDef(AllocSlotTyp.INVN_FLTR, 1, null, Map.of(), cond);
    }

    private AlocStgyDefinition.SlotDef multiSort(InvnSortField field) {
        return new AlocStgyDefinition.SlotDef(AllocSlotTyp.INVN_SRT, 1, AlocSrt.MULTI_SORT.name(),
                Map.of(AlocSrt.PARA_CRITERIA, List.of(Map.of("field", field.name(), "dir", "ASC"))),
                List.of());
    }
}
