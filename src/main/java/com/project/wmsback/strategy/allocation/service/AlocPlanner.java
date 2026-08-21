package com.project.wmsback.strategy.allocation.service;

import com.project.wmsback.strategy.allocation.component.AlocDstrb;
import com.project.wmsback.strategy.allocation.component.AlocRstrct;
import com.project.wmsback.strategy.allocation.dto.AlocDecisionTrace;
import com.project.wmsback.strategy.allocation.dto.AlocStgyDefinition;
import com.project.wmsback.strategy.allocation.dto.AlocGroupPlan;
import com.project.wmsback.strategy.allocation.entity.AlocSlotTyp;
import com.project.wmsback.strategy.allocation.field.AlocInvnField;
import com.project.wmsback.strategy.allocation.field.AlocLineField;
import com.project.wmsback.strategy.allocation.field.AlocInvnCandidate;
import com.project.wmsback.strategy.allocation.field.AlocLineTarget;
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
 * 할당 산정기 — 상품 그룹 하나를 받아 「어느 재고를 어느 라인에 얼마씩 줄지」를 계산한다.
 *
 * <p><b>순수 함수다.</b> 입력은 (전략 정의 · 라인 목록 · 후보 재고 스냅샷), 출력은
 * (라인별 배정 + 판정 근거)이며 재고도 DB도 건드리지 않는다. 덕분에 실전과 미리보기가
 * 이 산정 하나를 공유한다 (P4) — 실전은 「락 → 산정 → 예약」, 미리보기는 「락 없이 산정만」이라
 * 갈리는 지점이 전부 산정 바깥에 있다.
 *
 * <p><b>흐름</b>은 계층을 하나씩 도는 것이다. 계층마다 ① 쓸 수 있는 후보를 추려 정렬하고
 * ② 부족하면 분배 슬롯으로 라인별 상한을 정한 뒤 ③ 상한 안에서 재고를 소진하고
 * ④ 그래도 재고가 남으면 순차로 마저 배정한다. 계층을 다 돌면 라인별로 집계해 근거와 함께 낸다.
 *
 * <p><b>정의가 null이면 전부 기본 동작</b>이다: 계층 없음 · 점포 잔여수명 필터 · FEFO ·
 * 출고예정일순 · 순차 소진. 이것이 전략 도입 전의 붙박이 로직 그대로라, 전략을 하나도 만들지
 * 않은 창고에서 할당 결과가 달라지지 않는다.
 *
 * <p><b>전략이 건드릴 수 없는 것(하드 가드)</b>은 이 클래스가 무조건 적용한다 —
 * 유통기한 경과 Lot 제외 · 과할당 금지(상한 = 라인 잔여요청) · 정렬의 마지막 동률 해소.
 * 보관 로케이션 한정과 가용 &gt; 0은 후보 조회가 이미 강제한 상태로 들어온다.
 *
 * <p><b>산정 상태</b>(재고 잔량 · 라인 잔여요청 · 배정 · 제외 사유)는 인스턴스 필드로 들고
 * <b>인스턴스는 산정 1회마다 새로 만든다</b> — 상태를 파라미터로 돌리면 메서드마다 통째로
 * 끌고 다니게 되고, 공유 인스턴스를 두면 순수성이 깨진다.
 */
public final class AlocPlanner {

    /** 제외 사유를 남기는 최대 건수. 넘어가면 건수만 센다 — 근거 한 덩어리가 비대해지는 것을 막는다 */
    private static final int MAX_SKIP_TRACE = 50;

    // ── 입력 — 만들고 나면 바뀌지 않는다 ──────────────────────────────────────

    private final AlocStgyDefinition def;
    private final List<AlocStgyDefinition.SlotDef> rstrctSlots;
    private final List<AlocLineTarget> lines;              // 정렬된 라인
    private final List<AlocInvnCandidate> candidates;

    // ── 산정 상태 — 계층을 넘어가며 이어서 깎인다 ─────────────────────────────

    /** 후보별 남은 가용수량. 입력 레코드는 불변으로 두어 같은 목록으로 몇 번이든 다시 산정할 수 있다 */
    private final Map<Long, Long> stock = new LinkedHashMap<>();
    /** 라인별 남은 요청량 */
    private final Map<Long, Long> remainReq = new LinkedHashMap<>();
    /** 라인별 배정 결과 */
    private final Map<Long, List<AlocGroupPlan.Assignment>> assignments = new LinkedHashMap<>();
    /** 라인별 제외 사유. 같은 (재고, 라인) 조합은 한 번만 담는다 — 계층·sweep 패스에서 반복 평가되므로 */
    private final Map<Long, List<AlocGroupPlan.Skip>> skipsByLine = new LinkedHashMap<>();
    /** 위 중복 판정용 키 (라인id:재고id) */
    private final Set<String> skipKeys = new HashSet<>();
    private int skipCount;

    private AlocPlanner(AlocStgyDefinition def, List<AlocLineTarget> lines,
                              List<AlocInvnCandidate> candidates) {
        this.def = def;
        this.rstrctSlots = def != null ? def.slotsOf(AlocSlotTyp.RSTRCT) : List.of();
        this.candidates = candidates;

        List<AlocLineTarget> sorted = new ArrayList<>(lines);
        sorted.sort(lineComparator());
        this.lines = sorted;

        candidates.forEach(candidate -> stock.put(candidate.invId(), candidate.avalQty()));
        sorted.forEach(line -> remainReq.put(line.outbLineId(), line.reqQty()));
    }

    /**
     * 상품 그룹 하나를 산정한다.
     *
     * @param def        전략 정의. null = 전략 미설정(전부 기본 동작)
     * @param prodId     이 그룹의 상품 id (결과에 그대로 실린다)
     * @param prodCd     이 그룹의 상품코드 (결과·근거에 그대로 실린다)
     * @param lines      이 그룹의 라인들 (정렬 전)
     * @param candidates 이 그룹의 후보 재고 스냅샷 (보관 · 가용 &gt; 0이 이미 걸린 상태)
     */
    public static AlocGroupPlan plan(AlocStgyDefinition def, Long prodId, String prodCd,
                                      List<AlocLineTarget> lines, List<AlocInvnCandidate> candidates) {
        return new AlocPlanner(def, lines, candidates).run(prodId, prodCd);
    }

    /** 계층을 차례로 돌린 뒤, 남은 산정 상태를 라인별로 집계해 계획과 근거(trace)로 만든다 */
    private AlocGroupPlan run(Long prodId, String prodCd) {
        List<AlocStgyDefinition.SlotDef> tiers = def != null ? def.slotsOf(AlocSlotTyp.INVN_FLTR) : List.of();
        List<AlocDecisionTrace.TierTrace> tierTraces = new ArrayList<>();

        if (tiers.isEmpty()) {
            // 계층 없음 = 보관 재고 전체가 한 덩어리 (기본 동작)
            tierTraces.add(runTier(null, 1));
        } else {
            int seq = 1;
            for (AlocStgyDefinition.SlotDef tier : tiers) {
                tierTraces.add(runTier(tier, seq++));
            }
        }

        // 라인별 집계
        List<AlocGroupPlan.LinePlan> linePlans = new ArrayList<>();
        List<AlocDecisionTrace.SkipTrace> skipTraces = new ArrayList<>();
        long totalReq = 0;
        long totalAsgn = 0;
        for (AlocLineTarget line : lines) {
            List<AlocGroupPlan.Assignment> asgn = assignments.getOrDefault(line.outbLineId(), List.of());
            List<AlocGroupPlan.Skip> lineSkips = skipsByLine.getOrDefault(line.outbLineId(), List.of());
            long asgnQty = asgn.stream().mapToLong(AlocGroupPlan.Assignment::qty).sum();
            totalReq += line.reqQty();
            totalAsgn += asgnQty;
            linePlans.add(new AlocGroupPlan.LinePlan(line.outbLineId(), line.outbNo(),
                    line.storeCd(), line.prodCd(), line.reqQty(), asgnQty, asgn, lineSkips));
            for (AlocGroupPlan.Skip skip : lineSkips) {
                skipTraces.add(new AlocDecisionTrace.SkipTrace(line.outbNo(), line.storeCd(),
                        skip.locCd(), skip.lotNo(), skip.reason()));
            }
        }

        // 근거 — 결품 테이블이 없으므로 「왜 이만큼만 받았는지」가 남는 곳은 여기뿐이다
        AlocDecisionTrace trace = new AlocDecisionTrace(prodCd, totalReq, totalAsgn,
                describeSort(AlocSlotTyp.ODR_SRT, "출고예정일 ASC → 출고번호 ASC"),
                describeSort(AlocSlotTyp.INVN_SRT, "FEFO (유통기한 ASC → 피킹순위 → 로케이션코드)"),
                rstrctSlots.isEmpty()
                        ? "잔여수명 비율 · 점포 기준 (기본값)"
                        : rstrctSlots.stream().map(AlocStgyDefinition.SlotDef::cmpntCd).toList().toString(),
                tierTraces, skipTraces,
                skipCount > skipTraces.size() ? (long) (skipCount - skipTraces.size()) : null);

        return new AlocGroupPlan(prodId, prodCd, totalReq, totalAsgn, linePlans, trace);
    }

    // ── 계층 · 분배 ──────────────────────────────────────────────────────────

    /**
     * 계층 1회 — 이 계층의 후보로 추려 배정까지 마친다.
     *
     * <p>계층들은 산정 상태를 공유한다. 뒤 계층은 앞 계층이 깎고 남긴 잔량·잔여요청을 그대로
     * 이어받으므로, 이것이 곧 「앞 계층부터 소진」의 구현이다.
     *
     * @param tier 계층 슬롯. null = 계층 없음(후보 전체가 한 덩어리)
     * @param seq  근거에 남길 계층 순번
     */
    private AlocDecisionTrace.TierTrace runTier(AlocStgyDefinition.SlotDef tier, int seq) {
        String cond = tier != null ? describeCond(tier.condOrEmpty()) : "전체 (계층 없음)";

        // ① 후보 추리기 — 아직 남은 것 중 이 계층 조건에 맞는 것만, 재고 정렬 순서로
        List<AlocInvnCandidate> tierCandidates = candidates.stream()
                .filter(candidate -> stock.getOrDefault(candidate.invId(), 0L) > 0)
                .filter(candidate -> tier == null
                        || ConditionEvaluator.matchesAll(tier.condOrEmpty(), AlocInvnField.BY_CODE, candidate))
                .sorted(invnComparator())
                .toList();

        long tierAval = availOf(tierCandidates);
        List<AlocLineTarget> active = activeLines();
        long totalReq = active.stream().mapToLong(this::room).sum();

        if (tierAval <= 0 || active.isEmpty()) {
            return new AlocDecisionTrace.TierTrace(seq, cond, tierCandidates.size(), tierAval, totalReq,
                    tierAval <= 0 ? "후보 재고 없음" : "남은 요청 없음", null, null, null);
        }

        // ② 라인별 상한 — 부족할 때만 분배 슬롯이 개입한다
        boolean shortage = tierAval < totalReq;
        Map<Long, Long> caps;
        List<AlocDecisionTrace.DstrbTrace> dstrbTraces = null;
        if (!shortage) {
            // 부족이 아니다 — 분배 슬롯은 아예 평가하지 않는다
            caps = new LinkedHashMap<>();
            active.forEach(line -> caps.put(line.outbLineId(), room(line)));
        } else {
            DstrbOutcome outcome = distribute(tierAval, active);
            caps = outcome.caps();
            dstrbTraces = outcome.traces();
        }

        // ③ 상한 안에서 소진
        assign(tierCandidates, caps);

        // ④ 재고가 남았는데 못 받은 라인이 있으면 상한을 풀고 순차로 마저 배정한다.
        // 분배 산정은 그룹 수준이라 라인별 제약을 모른다 — 어떤 라인이 자기 상한을 다 쓰지 못하고
        // 남긴 재고를 다른 라인이 쓸 수 있는데도 놀리는 것은 부족 상황에서 특히 나쁘다.
        String sweep = null;
        if (availOf(tierCandidates) > 0 && activeLines().stream().anyMatch(line -> room(line) > 0)) {
            Map<Long, Long> sweepCaps = new LinkedHashMap<>();
            activeLines().forEach(line -> sweepCaps.put(line.outbLineId(), room(line)));
            assign(tierCandidates, sweepCaps);
            sweep = "제약 잔여 순차 배정";
        }
        return new AlocDecisionTrace.TierTrace(seq, cond, tierCandidates.size(), tierAval, totalReq,
                null, shortage, dstrbTraces, sweep);
    }

    /**
     * 부족할 때의 라인별 배분 상한을 산정한다 — <b>실제 배정은 하지 않는다.</b>
     * 슬롯을 앞에서부터 돌며 남은 가용을 나눠 주고, 슬롯이 0건이면 순차 소진 1건과 동치다.
     * 상한과 함께 슬롯별 근거({@code dstrb})를 돌려준다.
     */
    private DstrbOutcome distribute(long avalQty, List<AlocLineTarget> active) {
        List<AlocStgyDefinition.SlotDef> slots = def != null ? def.slotsOf(AlocSlotTyp.DSTRB) : List.of();
        Map<Long, Long> caps = new LinkedHashMap<>();
        List<AlocDecisionTrace.DstrbTrace> traces = new ArrayList<>();

        if (slots.isEmpty()) {
            caps.putAll(AlocDstrb.SEQUENTIAL.distribute(avalQty, active, this::room));
            traces.add(new AlocDecisionTrace.DstrbTrace(1, AlocDstrb.SEQUENTIAL.name(), true, null,
                    null, active.size(), caps.values().stream().mapToLong(Long::longValue).sum()));
            return new DstrbOutcome(caps, traces);
        }

        long left = avalQty;
        int seq = 1;
        for (AlocStgyDefinition.SlotDef slot : slots) {
            int slotSeq = seq++;
            String cond = describeCond(slot.condOrEmpty());

            if (left <= 0) {
                traces.add(new AlocDecisionTrace.DstrbTrace(slotSeq, slot.cmpntCd(), null, cond,
                        "SKIP — 남은 가용 없음", null, null));
                continue;
            }
            // 이미 준 상한을 빼고 봐야 슬롯끼리 같은 여유를 두 번 나눠 주지 않는다 (아래 람다도 같다)
            List<AlocLineTarget> targets = active.stream()
                    .filter(line -> ConditionEvaluator.matchesAll(
                            slot.condOrEmpty(), AlocLineField.BY_CODE, line))
                    .filter(line -> room(line) - caps.getOrDefault(line.outbLineId(), 0L) > 0)
                    .toList();
            if (targets.isEmpty()) {
                traces.add(new AlocDecisionTrace.DstrbTrace(slotSeq, slot.cmpntCd(), null, cond,
                        "SKIP — 대상 라인 없음", 0, null));
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
            traces.add(new AlocDecisionTrace.DstrbTrace(slotSeq, slot.cmpntCd(), null, cond,
                    null, targets.size(), given));
        }
        return new DstrbOutcome(caps, traces);
    }

    /** 분배 산정의 결과 묶음 — 라인별 상한과 슬롯별 근거를 함께 돌려준다 */
    private record DstrbOutcome(Map<Long, Long> caps, List<AlocDecisionTrace.DstrbTrace> traces) {
    }

    // ── 배정 ─────────────────────────────────────────────────────────────────

    /**
     * 상한(caps) 안에서 실제 배정을 만든다. 라인은 정렬 순서대로, 재고는 계층 정렬 순서대로 소진한다.
     * 제약·기한 판정이 <b>(재고, 라인) 조합</b>에 걸리므로 라인마다 후보를 다시 평가한다 —
     * 같은 상품이라도 A점포엔 되는 Lot이 B점포엔 안 될 수 있다.
     */
    private void assign(List<AlocInvnCandidate> tierCandidates, Map<Long, Long> caps) {
        for (AlocLineTarget line : lines) {
            long want = Math.min(caps.getOrDefault(line.outbLineId(), 0L), room(line));
            if (want <= 0) {
                continue;
            }
            for (AlocInvnCandidate candidate : tierCandidates) {
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
                // 재고 잔량 · 라인 잔여요청 · 배정을 한자리에서 함께 갱신한다
                long take = Math.min(avail, want);
                stock.put(candidate.invId(), avail - take);
                remainReq.merge(line.outbLineId(), -take, Long::sum);
                assignments.computeIfAbsent(line.outbLineId(), key -> new ArrayList<>())
                        .add(new AlocGroupPlan.Assignment(candidate.invId(),
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
    private String rejectReason(AlocInvnCandidate candidate, AlocLineTarget line) {
        if (candidate.expiryDt() != null && candidate.expiryDt().isBefore(line.expctDe())) {
            // 비율과 무관한 하드 가드 — 기준이 0%인 점포에도 기한 지난 Lot을 줄 수는 없다
            return "유통기한 경과 (" + candidate.expiryDt() + ")";
        }
        return rstrctReason(rstrctSlots, candidate, line);
    }

    /**
     * 제약 슬롯 판정 — <b>수동할당 후보 화면이 같은 판정을 표시</b>하므로 static으로 연다.
     * 화면이 점포 기준으로만 판정하면 전략이 고정 기준값을 쓸 때 「화면엔 통과, 자동할당은 거름」이
     * 생겨 화면을 믿을 수 없게 된다 ({@link AlocRstrct#lifeRate}를 public으로 둔 것과 같은 이유).
     *
     * <p>유통기한 경과 하드 가드는 여기 없다 — 호출부가 각자 먼저 건다(수동 후보는 아예 목록에서 뺀다).
     */
    public static String rstrctReason(List<AlocStgyDefinition.SlotDef> rstrctSlots,
                                      AlocInvnCandidate candidate, AlocLineTarget line) {
        if (rstrctSlots.isEmpty()) {
            return AlocRstrct.SHELF_LIFE_PCT.reject(candidate, line, Map.of()).orElse(null);
        }
        // 슬롯이 여럿이면 AND — 먼저 걸린 사유 하나만 돌려주고 나머지는 보지 않는다
        for (AlocStgyDefinition.SlotDef slot : rstrctSlots) {
            String reason = AlocRstrct.of(slot.cmpntCd())
                    .reject(candidate, line, slot.paraOrEmpty()).orElse(null);
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    // ── 산정 상태 조회 ───────────────────────────────────────────────────────

    /** 이 라인이 더 받을 수 있는 수량 */
    private long room(AlocLineTarget line) {
        return remainReq.getOrDefault(line.outbLineId(), 0L);
    }

    /** 이 후보 묶음에 남아 있는 가용수량 합계 */
    private long availOf(List<AlocInvnCandidate> group) {
        return group.stream().mapToLong(candidate -> stock.getOrDefault(candidate.invId(), 0L)).sum();
    }

    /** 아직 다 받지 못한 라인들 (정렬 순서 유지) */
    private List<AlocLineTarget> activeLines() {
        return lines.stream().filter(line -> room(line) > 0).toList();
    }

    // ── 정렬 ─────────────────────────────────────────────────────────────────

    /** 재고 정렬. 미설정이면 FEFO. 마지막에 inv_id를 붙여 언제 돌려도 같은 결과가 나오게 한다 */
    private Comparator<AlocInvnCandidate> invnComparator() {
        List<SortCriterion> criteria = criteriaOf(AlocSlotTyp.INVN_SRT);
        if (criteria.isEmpty()) {
            criteria = List.of(new SortCriterion(InvnSortField.EXPIRY_DT.name(), "ASC"),
                    new SortCriterion(InvnSortField.LOC_PIKNG_PRTY.name(), "ASC"),
                    new SortCriterion(InvnSortField.LOC_CD.name(), "ASC"));
        }
        Comparator<AlocInvnCandidate> comparator = null;
        for (SortCriterion criterion : criteria) {
            Comparator<AlocInvnCandidate> one =
                    InvnSortField.of(criterion.field()).comparator(criterion.asc());
            comparator = comparator == null ? one : comparator.thenComparing(one);
        }
        return comparator.thenComparing(AlocInvnCandidate::invId);
    }

    /** 라인 정렬. 미설정이면 출고예정일 → 출고번호. 마지막에 라인 id를 붙여 결정적이게 한다 */
    private Comparator<AlocLineTarget> lineComparator() {
        List<SortCriterion> criteria = criteriaOf(AlocSlotTyp.ODR_SRT);
        if (criteria.isEmpty()) {
            criteria = List.of(new SortCriterion(OdrSortField.EXPCT_DE.name(), "ASC"),
                    new SortCriterion(OdrSortField.OUTB_NO.name(), "ASC"));
        }
        Comparator<AlocLineTarget> comparator = null;
        for (SortCriterion criterion : criteria) {
            Comparator<AlocLineTarget> one =
                    OdrSortField.of(criterion.field()).comparator(criterion.asc());
            comparator = comparator == null ? one : comparator.thenComparing(one);
        }
        return comparator.thenComparing(AlocLineTarget::outbLineId);
    }

    /** 정렬 슬롯의 기준 목록. 전략이 없거나 슬롯을 등록하지 않았으면 빈 목록 = 기본값을 쓴다 */
    private List<SortCriterion> criteriaOf(AlocSlotTyp slotTyp) {
        if (def == null) {
            return List.of();
        }
        AlocStgyDefinition.SlotDef slot = def.singleSlot(slotTyp);
        return slot == null ? List.of() : slot.criteria();
    }

    // ── 근거(trace) 보조 ─────────────────────────────────────────────────────

    /** 제외 사유 1건 기록. 같은 조합은 계층·sweep 패스에서 여러 번 평가되므로 최초 1건만 남긴다 */
    private void addSkip(AlocInvnCandidate candidate, AlocLineTarget line, String reason) {
        if (!skipKeys.add(line.outbLineId() + ":" + candidate.invId())) {
            return;
        }
        skipCount++;
        if (skipCount > MAX_SKIP_TRACE) {
            return;
        }
        skipsByLine.computeIfAbsent(line.outbLineId(), key -> new ArrayList<>())
                .add(new AlocGroupPlan.Skip(candidate.invId(), candidate.locCd(),
                        candidate.lotNo(), reason));
    }

    /**
     * 이 재고가 속하는 계층 — <b>수동할당 후보 화면이 같은 판정을 표시</b>하므로 static으로 연다.
     * 계층이 없으면 전체가 한 계층(1)이고, 어느 계층에도 맞지 않으면 null — 자동할당은 그 재고를
     * 끝까지 배정하지 않는다({@link #runTier}가 계층별로 후보를 추리기 때문). 화면이 이것을 모르면
     * 잔여수명은 초록인데 자동할당은 절대 안 건드리는 재고가 생긴다.
     */
    public static Integer tierSeq(List<AlocStgyDefinition.SlotDef> tiers, AlocInvnCandidate candidate) {
        if (tiers.isEmpty()) {
            return 1;
        }
        int seq = 1;
        for (AlocStgyDefinition.SlotDef tier : tiers) {
            if (ConditionEvaluator.matchesAll(tier.condOrEmpty(), AlocInvnField.BY_CODE, candidate)) {
                return seq;
            }
            seq++;
        }
        return null;
    }

    /** 조건 목록을 화면에 그대로 쓸 한 줄로 */
    public static String describeCond(List<FieldCondition> conds) {
        if (conds == null || conds.isEmpty()) {
            return "조건 없음";
        }
        List<String> parts = new ArrayList<>();
        conds.forEach(cond -> parts.add(cond.fld() + " " + cond.op() + " " + cond.vals()));
        return String.join(" AND ", parts);
    }

    /** 정렬 기준을 화면에 그대로 쓸 한 줄로. 미설정이면 기본값 문구에 표시를 붙인다 */
    private String describeSort(AlocSlotTyp slotTyp, String dflt) {
        List<SortCriterion> criteria = criteriaOf(slotTyp);
        if (criteria.isEmpty()) {
            return dflt + " (기본값)";
        }
        List<String> parts = new ArrayList<>();
        criteria.forEach(criterion -> parts.add(criterion.field() + " " + (criterion.asc() ? "ASC" : "DESC")));
        return String.join(" → ", parts);
    }
}
