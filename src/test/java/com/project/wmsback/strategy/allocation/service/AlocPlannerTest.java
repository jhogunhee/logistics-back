package com.project.wmsback.strategy.allocation.service;

import com.project.wmsback.strategy.allocation.component.AlocDstrb;
import com.project.wmsback.strategy.allocation.component.AlocRstrct;
import com.project.wmsback.strategy.allocation.dto.AlocStgyDefinition;
import com.project.wmsback.strategy.allocation.dto.AlocGroupPlan;
import com.project.wmsback.strategy.allocation.entity.AlocSlotTyp;
import com.project.wmsback.strategy.allocation.field.AlocInvnCandidate;
import com.project.wmsback.strategy.allocation.field.AlocLineTarget;
import com.project.wmsback.strategy.allocation.field.InvnSortField;
import com.project.wmsback.strategy.allocation.field.OdrSortField;
import com.project.wmsback.strategy.core.condition.ConditionOperator;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 산정기의 규칙. 순수 함수라 목이 하나도 필요 없다 — 값 레코드를 넣고 계획을 받아 확인한다.
 *
 * <p>여기서 보는 것은 <b>전략이 켜졌을 때의 동작</b>이다. 전략 미설정 시 기본 동작이
 * 도입 전과 같은지는 {@code OutbAllocServiceTest}가 이미 지키고 있다.
 */
class AlocPlannerTest {

    private static final LocalDate EXPCT_DE = LocalDate.of(2026, 8, 10);

    // ── 분배 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("분배 슬롯이 없으면 순차 소진 — 앞 라인이 다 가져간다")
    void defaultsToSequential() {
        AlocGroupPlan plan = plan(null,
                List.of(line(1L, 30, "OB-001"), line(2L, 30, "OB-002")),
                List.of(candidate(1L, 40)));

        assertEquals(30, asgn(plan, 1L));
        assertEquals(10, asgn(plan, 2L));
    }

    @Test
    @DisplayName("주문 비율 — 가용을 주문수량 비율로 나누고 나머지는 앞에서부터 1씩")
    void ratioSplitsByOrderQty() {
        AlocGroupPlan plan = plan(def(dstrb(AlocDstrb.RATIO, List.of())),
                List.of(line(1L, 30, "OB-001"), line(2L, 10, "OB-002")),
                List.of(candidate(1L, 20)));

        // 20 × 30/40 = 15, 20 × 10/40 = 5
        assertEquals(15, asgn(plan, 1L));
        assertEquals(5, asgn(plan, 2L));
    }

    @Test
    @DisplayName("균등 — 주문수량과 무관하게 같은 수량씩")
    void equalSplitsEvenly() {
        AlocGroupPlan plan = plan(def(dstrb(AlocDstrb.EQUAL, List.of())),
                List.of(line(1L, 30, "OB-001"), line(2L, 30, "OB-002")),
                List.of(candidate(1L, 20)));

        assertEquals(10, asgn(plan, 1L));
        assertEquals(10, asgn(plan, 2L));
    }

    @Test
    @DisplayName("균등 — 요청보다 많이 배정될 라인은 상한으로 잘리고 남은 몫이 재배분된다")
    void equalRedistributesClampedRemainder() {
        AlocGroupPlan plan = plan(def(dstrb(AlocDstrb.EQUAL, List.of())),
                List.of(line(1L, 5, "OB-001"), line(2L, 100, "OB-002")),
                List.of(candidate(1L, 50)));

        // 1회차는 25/25지만 1번 라인은 5가 상한 — 남은 20이 2번으로 간다
        assertEquals(5, asgn(plan, 1L));
        assertEquals(45, asgn(plan, 2L));
        assertEquals(50, plan.asgnQty());
    }

    @Test
    @DisplayName("우선 분배 — 조건에 걸린 라인이 먼저 받고 나머지는 다음 슬롯이 받는다")
    void conditionalSlotGoesFirst() {
        AlocStgyDefinition definition = def(
                dstrb(AlocDstrb.SEQUENTIAL, List.of(
                        new FieldCondition("STORE_CD", ConditionOperator.IN, List.of("ST-0002")))),
                dstrb(AlocDstrb.SEQUENTIAL, List.of()));

        AlocGroupPlan plan = plan(definition,
                List.of(line(1L, 30, "OB-001", "ST-0001"), line(2L, 30, "OB-002", "ST-0002")),
                List.of(candidate(1L, 40)));

        // 정렬상 1번이 앞서지만 우선 슬롯이 2번(ST-0002)을 먼저 채운다
        assertEquals(30, asgn(plan, 2L));
        assertEquals(10, asgn(plan, 1L));
    }

    @Test
    @DisplayName("재고가 충분하면 분배 슬롯은 평가하지 않는다 — 전 라인 전량")
    void noShortageSkipsDistribution() {
        AlocGroupPlan plan = plan(def(dstrb(AlocDstrb.EQUAL, List.of())),
                List.of(line(1L, 10, "OB-001"), line(2L, 30, "OB-002")),
                List.of(candidate(1L, 100)));

        assertEquals(10, asgn(plan, 1L));
        assertEquals(30, asgn(plan, 2L));
    }

    // ── 계층 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("재고위치 계층 — 앞 계층을 다 쓰고 모자라면 다음 계층으로 내려간다")
    void tiersAreConsumedInOrder() {
        AlocStgyDefinition definition = new AlocStgyDefinition("계층", 0, List.of(), List.of(
                slot(AlocSlotTyp.INVN_FLTR, 1, null, Map.of(),
                        List.of(new FieldCondition("BIZ_DVSN", ConditionOperator.IN, List.of("PIKNG")))),
                slot(AlocSlotTyp.INVN_FLTR, 2, null, Map.of(), List.of())));

        AlocInvnCandidate picking = candidate(1L, 10, "PIKNG");
        AlocInvnCandidate storage = candidate(2L, 100, "STRG");

        AlocGroupPlan plan = plan(definition, List.of(line(1L, 30, "OB-001")),
                List.of(storage, picking));   // 입력 순서와 무관해야 한다

        assertEquals(30, asgn(plan, 1L));
        List<AlocGroupPlan.Assignment> assignments = plan.lines().get(0).assignments();
        assertEquals(10, assignments.get(0).qty());     // 피킹존 먼저 비운다
        assertEquals(1L, assignments.get(0).invId());
        assertEquals(20, assignments.get(1).qty());
    }

    @Test
    @DisplayName("계층 판정 — 첫 번째로 맞는 계층의 순번, 어디에도 안 맞으면 null, 계층이 없으면 1")
    void tierSeqMatchesFirstTierOrNull() {
        List<AlocStgyDefinition.SlotDef> tiers = List.of(
                slot(AlocSlotTyp.INVN_FLTR, 1, null, Map.of(),
                        List.of(new FieldCondition("BIZ_DVSN", ConditionOperator.IN, List.of("PIKNG")))));

        assertEquals(1, AlocPlanner.tierSeq(tiers, candidate(1L, 10, "PIKNG")));
        // 보관존 재고는 어느 계층에도 안 맞는다 — 자동할당은 건드리지 않고, 화면이 그것을 표시해야 한다
        assertNull(AlocPlanner.tierSeq(tiers, candidate(2L, 100, "STRG")));
        assertEquals(1, AlocPlanner.tierSeq(List.of(), candidate(2L, 100, "STRG")));
    }

    // ── 제약 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("잔여수명 고정 기준 — 미달 Lot은 사유와 함께 빠진다")
    void fixedShelfLifeExcludesWithReason() {
        AlocStgyDefinition definition = new AlocStgyDefinition("제약", 0, List.of(), List.of(
                slot(AlocSlotTyp.RSTRCT, 1, AlocRstrct.SHELF_LIFE_PCT.name(),
                        Map.of(AlocRstrct.PARA_BASIS, AlocRstrct.BASIS_FIXED,
                                AlocRstrct.PARA_MIN_PCT, 80), List.of())));

        // 총 수명 100일 중 남은 50일 = 50% → 고정 기준 80% 미달
        AlocInvnCandidate aged = new AlocInvnCandidate(1L, 1L, "A-01", 0, null,
                1L, "LOT-1", EXPCT_DE.minusDays(50), EXPCT_DE.plusDays(50), null, 100);

        AlocGroupPlan plan = plan(definition, List.of(line(1L, 10, "OB-001")), List.of(aged));

        assertEquals(0, asgn(plan, 1L));
        List<AlocGroupPlan.Skip> skips = plan.lines().get(0).skips();
        assertEquals(1, skips.size());
        assertTrue(skips.get(0).reason().contains("50.0%"), skips.get(0).reason());
    }

    @Test
    @DisplayName("유통기한 경과 Lot은 전략과 무관하게 제외된다 — 하드 가드")
    void expiredLotIsAlwaysExcluded() {
        // 제약 슬롯을 기준 0%로 두어 "비율 필터는 통과"하는 상황을 만든다
        AlocStgyDefinition definition = new AlocStgyDefinition("기한", 0, List.of(), List.of(
                slot(AlocSlotTyp.RSTRCT, 1, AlocRstrct.SHELF_LIFE_PCT.name(),
                        Map.of(AlocRstrct.PARA_BASIS, AlocRstrct.BASIS_FIXED,
                                AlocRstrct.PARA_MIN_PCT, 0), List.of())));

        AlocInvnCandidate expired = new AlocInvnCandidate(1L, 1L, "A-01", 0, null,
                1L, "LOT-1", EXPCT_DE.minusDays(100), EXPCT_DE.minusDays(1), null, 100);

        AlocGroupPlan plan = plan(definition, List.of(line(1L, 10, "OB-001")), List.of(expired));

        assertEquals(0, asgn(plan, 1L));
        assertTrue(plan.lines().get(0).skips().get(0).reason().contains("유통기한 경과"));
    }

    // ── 정렬 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("재고 정렬 — 정의한 기준이 FEFO 기본값을 덮어쓴다")
    void invnSortOverridesFefo() {
        AlocStgyDefinition definition = new AlocStgyDefinition("정렬", 0, List.of(), List.of(
                slot(AlocSlotTyp.INVN_SRT, 1, null,
                        Map.of(AlocStgyDefinition.SlotDef.PARA_CRITERIA, List.of(
                                Map.of("field", InvnSortField.AVAL_QTY.name(), "dir", "DESC"))),
                        List.of())));

        AlocInvnCandidate small = new AlocInvnCandidate(1L, 1L, "A-01", 0, null,
                1L, "LOT-1", null, EXPCT_DE.plusDays(10), null, 5);     // FEFO라면 이쪽이 먼저
        AlocInvnCandidate big = new AlocInvnCandidate(2L, 2L, "A-02", 0, null,
                2L, "LOT-2", null, EXPCT_DE.plusDays(999), null, 50);

        AlocGroupPlan plan = plan(definition, List.of(line(1L, 10, "OB-001")), List.of(small, big));

        assertEquals(2L, plan.lines().get(0).assignments().get(0).invId());
    }

    @Test
    @DisplayName("주문 정렬 — 주문수량 내림차순이면 큰 주문이 먼저 가져간다")
    void odrSortChangesPriority() {
        AlocStgyDefinition definition = new AlocStgyDefinition("주문정렬", 0, List.of(), List.of(
                slot(AlocSlotTyp.ODR_SRT, 1, null,
                        Map.of(AlocStgyDefinition.SlotDef.PARA_CRITERIA, List.of(
                                Map.of("field", OdrSortField.ODR_QTY.name(), "dir", "DESC"))),
                        List.of())));

        AlocGroupPlan plan = plan(definition,
                List.of(line(1L, 10, "OB-001"), line(2L, 50, "OB-002")),
                List.of(candidate(1L, 50)));

        assertEquals(50, asgn(plan, 2L));
        assertEquals(0, asgn(plan, 1L));
    }

    // ── 과할당 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("기할당분을 뺀 잔여요청이 상한이다 — 전략이 과할당을 열 수 없다")
    void alreadyAllocatedCapsRequest() {
        AlocLineTarget partial = new AlocLineTarget(1L, 1L, "OB-001", 1L, "PROD-0001",
                "ST-0001", "점포", null, null, (short) 0, "NRML", null, EXPCT_DE, 30, 25);

        AlocGroupPlan plan = plan(null, List.of(partial), List.of(candidate(1L, 100)));

        assertEquals(5, asgn(plan, 1L));
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────────

    private AlocGroupPlan plan(AlocStgyDefinition def, List<AlocLineTarget> lines,
                                List<AlocInvnCandidate> candidates) {
        return AlocPlanner.plan(def, 1L, "PROD-0001", lines, candidates);
    }

    private long asgn(AlocGroupPlan plan, Long outbLineId) {
        return plan.lines().stream()
                .filter(line -> line.outbLineId().equals(outbLineId))
                .findFirst().orElseThrow().asgnQty();
    }

    /** 분배 슬롯만 가진 정의 */
    private AlocStgyDefinition def(AlocStgyDefinition.SlotDef... dstrbSlots) {
        return new AlocStgyDefinition("테스트", 0, List.of(), List.of(dstrbSlots));
    }

    private AlocStgyDefinition.SlotDef dstrb(AlocDstrb cmpnt, List<FieldCondition> cond) {
        return slot(AlocSlotTyp.DSTRB, cond.isEmpty() ? 9 : 1, cmpnt.name(), Map.of(), cond);
    }

    private AlocStgyDefinition.SlotDef slot(AlocSlotTyp slotTyp, int srtSeq, String cmpntCd,
                                            Map<String, Object> para, List<FieldCondition> cond) {
        return new AlocStgyDefinition.SlotDef(slotTyp, srtSeq, cmpntCd, para, cond);
    }

    /** 잔여수명이 넉넉한 후보 (제조일자를 출고예정일 직전에 둔다) */
    private AlocInvnCandidate candidate(long invId, long avalQty) {
        return candidate(invId, avalQty, null);
    }

    private AlocInvnCandidate candidate(long invId, long avalQty, String bizDvsn) {
        return new AlocInvnCandidate(invId, invId, "A-0" + invId, 0, bizDvsn,
                invId, "LOT-" + invId, EXPCT_DE.minusDays(1), EXPCT_DE.plusDays(99), null, avalQty);
    }

    private AlocLineTarget line(long lineId, long odrQty, String outbNo) {
        return line(lineId, odrQty, outbNo, "ST-0001");
    }

    private AlocLineTarget line(long lineId, long odrQty, String outbNo, String storeCd) {
        return new AlocLineTarget(lineId, lineId, outbNo, 1L, "PROD-0001",
                storeCd, "점포", null, null, (short) 50, "NRML", null, EXPCT_DE, odrQty, 0);
    }
}
