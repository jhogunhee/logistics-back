package com.project.wmsback.strategy.allocation.service;

import com.project.wmsback.strategy.allocation.component.AlocDstrb;
import com.project.wmsback.strategy.allocation.component.AlocRstrct;
import com.project.wmsback.strategy.allocation.component.AlocSrt;
import com.project.wmsback.strategy.allocation.dto.AlocStgyDefinition;
import com.project.wmsback.strategy.allocation.dto.AllocGroupPlan;
import com.project.wmsback.strategy.allocation.entity.AllocSlotTyp;
import com.project.wmsback.strategy.allocation.field.AlocInvnField;
import com.project.wmsback.strategy.allocation.field.AlocLineField;
import com.project.wmsback.strategy.allocation.field.AllocInvnCandidate;
import com.project.wmsback.strategy.allocation.field.AllocLineTarget;
import com.project.wmsback.strategy.allocation.field.InvnSortField;
import com.project.wmsback.strategy.allocation.field.OdrSortField;
import com.project.wmsback.strategy.core.condition.ConditionEvaluator;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.condition.SortCriterion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 할당 산정기 — <b>순수 함수다.</b> 입력은 (정의, 라인 목록, 후보 재고 스냅샷)이고
 * 출력은 (라인별 배정 + 판정 근거)이며, 재고도 DB도 건드리지 않는다.
 *
 * <p>그래서 실전과 미리보기가 이 산정 하나를 공유한다 (P4) — 실전은 「락 → 산정 → 예약」,
 * 미리보기는 「락 없이 산정만」이라 갈리는 지점이 산정 바깥에만 있다.
 *
 * <p><b>정의가 null이면 전부 기본 동작</b>이다: 계층 없음 · 점포 잔여수명 필터 · FEFO ·
 * 출고예정일순 · 순차 소진. 이것이 전략 도입 전의 붙박이 로직 그대로라, 전략을 하나도 만들지
 * 않은 창고에서 할당 결과가 달라지지 않는다.
 *
 * <p>전략이 건드릴 수 없는 것(하드 가드)은 이 클래스가 무조건 적용한다 —
 * 유통기한 경과 Lot 제외 · 과할당 금지(상한 = 라인 잔여요청) · 정렬의 마지막 동률 해소.
 * 보관 로케이션 한정과 가용 &gt; 0은 후보 조회가 이미 강제한 상태로 들어온다.
 *
 * <p>산정 상태(재고 잔량 · 라인 잔여요청)를 인스턴스 필드로 들고 <b>인스턴스는 산정 1회마다
 * 새로 만든다</b> — 상태를 파라미터로 돌리면 메서드마다 장부 6개를 끌고 다니게 되고,
 * 공유 인스턴스를 두면 순수성이 깨진다.
 */
public final class AllocationPlanner {

    /** trace에 담을 제외 사유의 최대 건수. 넘어가면 건수만 남긴다 — 로그 한 건이 비대해지는 것을 막는다 */
    private static final int MAX_SKIP_TRACE = 50;

    private final AlocStgyDefinition def;
    private final List<AlocStgyDefinition.SlotDef> rstrctSlots;
    private final List<AllocLineTarget> lines;              // 정렬된 라인
    private final List<AllocInvnCandidate> candidates;

    /** 후보별 남은 가용수량. 입력 레코드는 불변으로 두어 같은 목록으로 몇 번이든 다시 산정할 수 있다 */
    private final Map<Long, Long> stock = new LinkedHashMap<>();
    /** 라인별 남은 요청량 */
    private final Map<Long, Long> remainReq = new LinkedHashMap<>();
    private final Map<Long, List<AllocGroupPlan.Assignment>> assignments = new LinkedHashMap<>();
    /** 라인별 제외 사유. 같은 (재고, 라인) 조합은 한 번만 담는다 — 계층·정리 패스에서 반복 평가되므로 */
    private final Map<Long, List<AllocGroupPlan.Skip>> skipsByLine = new LinkedHashMap<>();
    private final Set<String> skipKeys = new HashSet<>();
    private int skipCount;

    private AllocationPlanner(AlocStgyDefinition def, List<AllocLineTarget> lines,
                              List<AllocInvnCandidate> candidates) {
        this.def = def;
        this.rstrctSlots = def != null ? def.slotsOf(AllocSlotTyp.RSTRCT) : List.of();
        this.candidates = candidates;

        List<AllocLineTarget> sorted = new ArrayList<>(lines);
        sorted.sort(lineComparator());
        this.lines = sorted;

        candidates.forEach(candidate -> stock.put(candidate.invId(), candidate.avalQty()));
        sorted.forEach(line -> remainReq.put(line.outbLineId(), line.reqQty()));
    }

    /**
     * 상품 그룹 하나를 산정한다.
     *
     * @param def        전략 정의. null = 전략 미설정(전부 기본 동작)
     * @param lines      이 그룹의 라인들 (정렬 전)
     * @param candidates 이 그룹의 후보 재고 스냅샷 (보관 · 가용 &gt; 0이 이미 걸린 상태)
     */
    public static AllocGroupPlan plan(AlocStgyDefinition def, Long prodId, String prodCd,
                                      List<AllocLineTarget> lines, List<AllocInvnCandidate> candidates) {
        return new AllocationPlanner(def, lines, candidates).run(prodId, prodCd);
    }

    private AllocGroupPlan run(Long prodId, String prodCd) {
        List<AlocStgyDefinition.SlotDef> tiers = def != null ? def.slotsOf(AllocSlotTyp.INVN_FLTR) : List.of();
        List<Map<String, Object>> tierTraces = new ArrayList<>();

        if (tiers.isEmpty()) {
            // 계층 없음 = 보관 재고 전체가 한 덩어리 (기본 동작)
            tierTraces.add(runTier(null, 1));
        } else {
            int seq = 1;
            for (AlocStgyDefinition.SlotDef tier : tiers) {
                tierTraces.add(runTier(tier, seq++));
            }
        }

        List<AllocGroupPlan.LinePlan> linePlans = new ArrayList<>();
        List<Map<String, Object>> skipTraces = new ArrayList<>();
        long totalReq = 0;
        long totalAsgn = 0;
        for (AllocLineTarget line : lines) {
            List<AllocGroupPlan.Assignment> asgn = assignments.getOrDefault(line.outbLineId(), List.of());
            List<AllocGroupPlan.Skip> lineSkips = skipsByLine.getOrDefault(line.outbLineId(), List.of());
            long asgnQty = asgn.stream().mapToLong(AllocGroupPlan.Assignment::qty).sum();
            totalReq += line.reqQty();
            totalAsgn += asgnQty;
            linePlans.add(new AllocGroupPlan.LinePlan(line.outbLineId(), line.outbNo(),
                    line.storeCd(), line.prodCd(), line.reqQty(), asgnQty, asgn, lineSkips));
            for (AllocGroupPlan.Skip skip : lineSkips) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("outbNo", line.outbNo());
                entry.put("storeCd", line.storeCd());
                entry.put("locCd", skip.locCd());
                entry.put("lotNo", skip.lotNo());
                entry.put("reason", skip.reason());
                skipTraces.add(entry);
            }
        }

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("prodCd", prodCd);
        trace.put("reqQty", totalReq);
        trace.put("asgnQty", totalAsgn);
        trace.put("odrSrt", describeSort(AllocSlotTyp.ODR_SRT, "출고예정일 ASC → 출고번호 ASC"));
        trace.put("invnSrt", describeSort(AllocSlotTyp.INVN_SRT, "FEFO (유통기한 ASC → 피킹순위 → 로케이션코드)"));
        trace.put("rstrct", rstrctSlots.isEmpty()
                ? "잔여수명 비율 · 점포 기준 (기본값)"
                : rstrctSlots.stream().map(AlocStgyDefinition.SlotDef::cmpntCd).toList().toString());
        trace.put("tiers", tierTraces);
        trace.put("skips", skipTraces);
        if (skipCount > skipTraces.size()) {
            trace.put("skipsOmitted", skipCount - skipTraces.size());
        }

        return new AllocGroupPlan(prodId, prodCd, totalReq, totalAsgn, linePlans, trace);
    }

    // ── 계층 1회 ─────────────────────────────────────────────────────────────

    private Map<String, Object> runTier(AlocStgyDefinition.SlotDef tier, int seq) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("seq", seq);
        trace.put("cond", tier != null ? describeCond(tier.condOrEmpty()) : "전체 (계층 없음)");

        List<AllocInvnCandidate> tierCandidates = candidates.stream()
                .filter(candidate -> stock.getOrDefault(candidate.invId(), 0L) > 0)
                .filter(candidate -> tier == null
                        || ConditionEvaluator.matchesAll(tier.condOrEmpty(), AlocInvnField.BY_CODE, candidate))
                .sorted(invnComparator())
                .toList();

        long tierAval = availOf(tierCandidates);
        List<AllocLineTarget> active = activeLines();
        long totalReq = active.stream().mapToLong(this::room).sum();

        trace.put("cndtCnt", tierCandidates.size());
        trace.put("avalQty", tierAval);
        trace.put("reqQty", totalReq);

        if (tierAval <= 0 || active.isEmpty()) {
            trace.put("result", tierAval <= 0 ? "후보 재고 없음" : "남은 요청 없음");
            return trace;
        }

        Map<Long, Long> caps;
        if (tierAval >= totalReq) {
            // 부족이 아니다 — 분배 슬롯은 아예 평가하지 않는다
            trace.put("shortage", false);
            caps = new LinkedHashMap<>();
            active.forEach(line -> caps.put(line.outbLineId(), room(line)));
        } else {
            trace.put("shortage", true);
            caps = distribute(tierAval, active, trace);
        }

        assign(tierCandidates, caps);

        // 제약으로 못 쓴 재고가 남고 아직 못 받은 라인이 있으면 순차로 마저 배정한다.
        // 분배 산정은 그룹 수준이라 라인별 제약을 모른다 — 어떤 라인이 자기 상한을 다 쓰지 못하고
        // 남긴 재고를 다른 라인이 쓸 수 있는데도 놀리는 것은 부족 상황에서 특히 나쁘다.
        if (availOf(tierCandidates) > 0 && activeLines().stream().anyMatch(line -> room(line) > 0)) {
            Map<Long, Long> sweep = new LinkedHashMap<>();
            activeLines().forEach(line -> sweep.put(line.outbLineId(), room(line)));
            assign(tierCandidates, sweep);
            trace.put("sweep", "제약 잔여 순차 배정");
        }
        return trace;
    }

    /** 분배 슬롯 순회. 슬롯이 0건이면 순차 소진 1건과 동치다 */
    private Map<Long, Long> distribute(long avalQty, List<AllocLineTarget> active,
                                       Map<String, Object> tierTrace) {
        List<AlocStgyDefinition.SlotDef> slots = def != null ? def.slotsOf(AllocSlotTyp.DSTRB) : List.of();
        Map<Long, Long> caps = new LinkedHashMap<>();
        List<Map<String, Object>> traces = new ArrayList<>();

        if (slots.isEmpty()) {
            caps.putAll(AlocDstrb.SEQUENTIAL.distribute(avalQty, active, this::room));
            Map<String, Object> dflt = new LinkedHashMap<>();
            dflt.put("seq", 1);
            dflt.put("cmpntCd", AlocDstrb.SEQUENTIAL.name());
            dflt.put("dflt", true);
            dflt.put("tgtLineCnt", active.size());
            dflt.put("asgnQty", caps.values().stream().mapToLong(Long::longValue).sum());
            traces.add(dflt);
            tierTrace.put("dstrb", traces);
            return caps;
        }

        long left = avalQty;
        int seq = 1;
        for (AlocStgyDefinition.SlotDef slot : slots) {
            Map<String, Object> slotTrace = new LinkedHashMap<>();
            slotTrace.put("seq", seq++);
            slotTrace.put("cmpntCd", slot.cmpntCd());
            slotTrace.put("cond", describeCond(slot.condOrEmpty()));
            traces.add(slotTrace);

            if (left <= 0) {
                slotTrace.put("result", "SKIP — 남은 가용 없음");
                continue;
            }
            List<AllocLineTarget> targets = active.stream()
                    .filter(line -> ConditionEvaluator.matchesAll(
                            slot.condOrEmpty(), AlocLineField.BY_CODE, line))
                    .filter(line -> room(line) - caps.getOrDefault(line.outbLineId(), 0L) > 0)
                    .toList();
            slotTrace.put("tgtLineCnt", targets.size());
            if (targets.isEmpty()) {
                slotTrace.put("result", "SKIP — 대상 라인 없음");
                continue;
            }

            Map<Long, Long> slotCaps = AlocDstrb.of(slot.cmpntCd()).distribute(left, targets,
                    line -> room(line) - caps.getOrDefault(line.outbLineId(), 0L));
            long given = 0;
            for (Map.Entry<Long, Long> entry : slotCaps.entrySet()) {
                if (entry.getValue() > 0) {
                    caps.merge(entry.getKey(), entry.getValue(), Long::sum);
                    given += entry.getValue();
                }
            }
            left -= given;
            slotTrace.put("asgnQty", given);
        }
        tierTrace.put("dstrb", traces);
        return caps;
    }

    // ── 배정 ─────────────────────────────────────────────────────────────────

    /**
     * 상한(caps) 안에서 실제 배정을 만든다. 라인은 정렬 순서대로, 재고는 계층 정렬 순서대로 소진한다.
     * 제약·기한 판정이 <b>(재고, 라인) 조합</b>에 걸리므로 라인마다 후보를 다시 평가한다 —
     * 같은 상품이라도 A점포엔 되는 Lot이 B점포엔 안 될 수 있다.
     */
    private void assign(List<AllocInvnCandidate> tierCandidates, Map<Long, Long> caps) {
        for (AllocLineTarget line : lines) {
            long want = Math.min(caps.getOrDefault(line.outbLineId(), 0L), room(line));
            if (want <= 0) {
                continue;
            }
            for (AllocInvnCandidate candidate : tierCandidates) {
                if (want <= 0) {
                    break;
                }
                long avail = stock.getOrDefault(candidate.invId(), 0L);
                if (avail <= 0) {
                    continue;
                }
                String reason = rejectReason(candidate, line);
                if (reason != null) {
                    addSkip(candidate, line, reason);
                    continue;
                }
                long take = Math.min(avail, want);
                stock.put(candidate.invId(), avail - take);
                remainReq.merge(line.outbLineId(), -take, Long::sum);
                assignments.computeIfAbsent(line.outbLineId(), key -> new ArrayList<>())
                        .add(new AllocGroupPlan.Assignment(candidate.invId(),
                                candidate.locCd(), candidate.lotNo(), take));
                want -= take;
            }
        }
    }

    /**
     * 이 (재고, 라인) 조합이 제외되는 사유. null이면 통과.
     *
     * <p>제약 슬롯이 하나도 없으면 <b>점포 기준 잔여수명</b>이 적용된다 — 현행 붙박이 동작이
     * 기본값이라, 제약을 등록하지 않았다고 필터가 꺼지지는 않는다. 슬롯을 등록하면 그 정의가
     * 기본값을 대체한다(고정 기준값으로 바꾸는 등).
     */
    private String rejectReason(AllocInvnCandidate candidate, AllocLineTarget line) {
        if (candidate.expiryDt() != null && candidate.expiryDt().isBefore(line.expctDe())) {
            // 비율과 무관한 하드 가드 — 기준이 0%인 점포에도 기한 지난 Lot을 줄 수는 없다
            return "유통기한 경과 (" + candidate.expiryDt() + ")";
        }
        if (rstrctSlots.isEmpty()) {
            return AlocRstrct.SHELF_LIFE_PCT.reject(candidate, line, Map.of()).orElse(null);
        }
        for (AlocStgyDefinition.SlotDef slot : rstrctSlots) {
            String reason = AlocRstrct.of(slot.cmpntCd())
                    .reject(candidate, line, slot.paraOrEmpty()).orElse(null);
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    // ── 장부 조회 ────────────────────────────────────────────────────────────

    private long room(AllocLineTarget line) {
        return remainReq.getOrDefault(line.outbLineId(), 0L);
    }

    private long availOf(List<AllocInvnCandidate> group) {
        return group.stream().mapToLong(candidate -> stock.getOrDefault(candidate.invId(), 0L)).sum();
    }

    private List<AllocLineTarget> activeLines() {
        return lines.stream().filter(line -> room(line) > 0).toList();
    }

    // ── 정렬 ─────────────────────────────────────────────────────────────────

    /** 재고 정렬. 미설정이면 FEFO. 마지막에 inv_id를 붙여 언제 돌려도 같은 결과가 나오게 한다 */
    private Comparator<AllocInvnCandidate> invnComparator() {
        List<SortCriterion> criteria = criteriaOf(AllocSlotTyp.INVN_SRT);
        if (criteria.isEmpty()) {
            criteria = List.of(new SortCriterion(InvnSortField.EXPIRY_DT.name(), "ASC"),
                    new SortCriterion(InvnSortField.LOC_PIKNG_PRTY.name(), "ASC"),
                    new SortCriterion(InvnSortField.LOC_CD.name(), "ASC"));
        }
        Comparator<AllocInvnCandidate> comparator = null;
        for (SortCriterion criterion : criteria) {
            Comparator<AllocInvnCandidate> one =
                    InvnSortField.of(criterion.field()).comparator(criterion.asc());
            comparator = comparator == null ? one : comparator.thenComparing(one);
        }
        return comparator.thenComparing(AllocInvnCandidate::invId);
    }

    /** 라인 정렬. 미설정이면 출고예정일 → 출고번호. 마지막에 라인 id를 붙여 결정적이게 한다 */
    private Comparator<AllocLineTarget> lineComparator() {
        List<SortCriterion> criteria = criteriaOf(AllocSlotTyp.ODR_SRT);
        if (criteria.isEmpty()) {
            criteria = List.of(new SortCriterion(OdrSortField.EXPCT_DE.name(), "ASC"),
                    new SortCriterion(OdrSortField.OUTB_NO.name(), "ASC"));
        }
        Comparator<AllocLineTarget> comparator = null;
        for (SortCriterion criterion : criteria) {
            Comparator<AllocLineTarget> one =
                    OdrSortField.of(criterion.field()).comparator(criterion.asc());
            comparator = comparator == null ? one : comparator.thenComparing(one);
        }
        return comparator.thenComparing(AllocLineTarget::outbLineId);
    }

    private List<SortCriterion> criteriaOf(AllocSlotTyp slotTyp) {
        if (def == null) {
            return List.of();
        }
        AlocStgyDefinition.SlotDef slot = def.singleSlot(slotTyp);
        return slot == null ? List.of() : AlocSrt.criteriaOf(slot.paraOrEmpty());
    }

    // ── trace 보조 ───────────────────────────────────────────────────────────

    private void addSkip(AllocInvnCandidate candidate, AllocLineTarget line, String reason) {
        // 같은 조합은 계층·정리 패스에서 여러 번 평가되므로 최초 1건만 남긴다
        if (!skipKeys.add(line.outbLineId() + ":" + candidate.invId())) {
            return;
        }
        skipCount++;
        if (skipCount > MAX_SKIP_TRACE) {
            return;
        }
        skipsByLine.computeIfAbsent(line.outbLineId(), key -> new ArrayList<>())
                .add(new AllocGroupPlan.Skip(candidate.invId(), candidate.locCd(),
                        candidate.lotNo(), reason));
    }

    private static String describeCond(List<FieldCondition> conds) {
        if (conds == null || conds.isEmpty()) {
            return "조건 없음";
        }
        List<String> parts = new ArrayList<>();
        conds.forEach(cond -> parts.add(cond.fld() + " " + cond.op() + " " + cond.vals()));
        return String.join(" AND ", parts);
    }

    private String describeSort(AllocSlotTyp slotTyp, String dflt) {
        List<SortCriterion> criteria = criteriaOf(slotTyp);
        if (criteria.isEmpty()) {
            return dflt + " (기본값)";
        }
        List<String> parts = new ArrayList<>();
        criteria.forEach(criterion -> parts.add(criterion.field() + " " + (criterion.asc() ? "ASC" : "DESC")));
        return String.join(" → ", parts);
    }
}
