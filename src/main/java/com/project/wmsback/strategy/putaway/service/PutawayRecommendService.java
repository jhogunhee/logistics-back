package com.project.wmsback.strategy.putaway.service;

import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.strategy.core.condition.ConditionEvaluator;
import com.project.wmsback.strategy.core.condition.SortCriterion;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import com.project.wmsback.inventory.service.LocCapacityService;
import com.project.wmsback.strategy.putaway.dto.PtawyPreviewRequest;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyDefinition;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyResponse;
import com.project.wmsback.strategy.putaway.dto.PutawayBulkRecommendRequest;
import com.project.wmsback.strategy.putaway.dto.PutawayBulkRecommendResponse;
import com.project.wmsback.strategy.putaway.dto.PutawayDecisionTrace;
import com.project.wmsback.strategy.putaway.dto.PutawayRecommendResponse;
import com.project.wmsback.strategy.putaway.entity.PtawyStgy;
import com.project.wmsback.strategy.putaway.field.PutawayLocField;
import com.project.wmsback.strategy.putaway.field.PutawaySortField;
import com.project.wmsback.strategy.putaway.field.PutawayTarget;
import com.project.wmsback.strategy.putaway.field.PutawayTargetField;
import com.project.wmsback.strategy.putaway.component.PutawayMethod;
import com.project.wmsback.strategy.putaway.component.PutawayMethodContext;
import com.project.wmsback.strategy.putaway.repository.PtawyStgyRepository;
import com.project.wmsback.strategy.putaway.repository.PutawayQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 적치 추천 산정. 전략은 <b>지시 생성 후보</b>를 만든다 — 이 결과를 사람이 확인해 적치지시로 저장하고,
 * 실행(실물 MOVE)은 그 지시를 소진한다. 추천 자체는 예약이 아니라서 추천과 지시 생성 사이의 재고·용량
 * 변동은 생성 측 가드가 같은 식으로 다시 검증한다 (docs/design.md 「적치 지시」: 2회 검증).
 * 작업자 일괄 추천(recommendBulk)과 관리자 미리보기(preview)가 같은 산정 함수(compute)를 공유한다 (P4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayRecommendService {

    private final PtawyStgyRepository ptawyStgyRepository;
    private final IbLineRepository ibLineRepository;
    private final ProdRepository prodRepository;
    private final PutawayQueryRepository putawayQueryRepository;
    private final LocCapacityService locCapacityService;
    private final StgyExecLogService stgyExecLogService;

    /**
     * 적치지시 일괄 추천 — 배치 여러 건을 받은 순서대로 산정한다.
     * <p>
     * 배치마다 따로 추천하면 같은 로케이션의 남은 자리를 여러 배치가 각자 다 쓸 수 있다고 보고
     * 이중 배정한다. 그래서 이번 호출의 배정을 {@link CrossBatch}에 누적해 다음 배치의 후보
     * 현황에 미리 반영한다 — 화면이 순차 호출하던 시절의 결함을 서버가 흡수한 지점이다.
     */
    public PutawayBulkRecommendResponse recommendBulk(PutawayBulkRecommendRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("추천할 배치가 없습니다.");
        }
        Map<Long, Long> inflowByLoc = locCapacityService.openInflowQtyByLoc();
        CrossBatch crossBatch = new CrossBatch();

        List<PutawayBulkRecommendResponse.Item> items = new ArrayList<>();
        for (PutawayBulkRecommendRequest.Item item : request.items()) {
            items.add(recommendOne(item, inflowByLoc, crossBatch));
        }
        return new PutawayBulkRecommendResponse(items);
    }

    private PutawayBulkRecommendResponse.Item recommendOne(PutawayBulkRecommendRequest.Item item,
                                                           Map<Long, Long> inflowByLoc,
                                                           CrossBatch crossBatch) {
        if (item.qty() == null || item.qty() < 1) {
            throw new IllegalArgumentException("추천할 수량은 1 이상이어야 합니다.");
        }
        IbLine ibLine = ibLineRepository.findById(item.ibLineId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + item.ibLineId()));
        Prod prod = ibLine.getProd();
        PutawayTarget target = new PutawayTarget(prod, ibLine.getIbOrder().getVendor().getVndrCd());

        Optional<PtawyStgy> selected = selectStrategy(ibLine.getIbOrder().getOdrDvsn());
        if (selected.isEmpty()) {
            // 전략 미설정 — 화면이 이 배치를 수동 지시로 안내한다
            return new PutawayBulkRecommendResponse.Item(item.ibLineId(), item.lotId(),
                    prod.getProdCd(), prod.getProdNm(), false, null, null,
                    item.qty(), 0, item.qty(), List.of());
        }
        PtawyStgy stgy = selected.get();
        PutawayRecommendResponse result = compute(PtawyStgyResponse.from(stgy).toDefinition(),
                stgy.getId(), stgy.getStgyNm(), stgy.getLastRvsnNo(), prod, target, item.qty(),
                inflowByLoc, crossBatch);

        // 다음 배치가 같은 자리를 다시 쓰지 않도록 이번 배정분을 누적
        List<PutawayBulkRecommendResponse.Assignment> assignments = new ArrayList<>();
        for (PutawayRecommendResponse.Assignment assignment : result.assignments()) {
            crossBatch.add(assignment.locId(), prod.getId(), assignment.qty());
            assignments.add(new PutawayBulkRecommendResponse.Assignment(
                    assignment.locId(), assignment.locCd(), assignment.qty()));
        }

        // 추천은 지시가 아니다 — 눌러본 횟수만큼 실행 이력에 섞이지 않게 PREVIEW로 남긴다.
        // 그래도 남기는 이유는 지시 생성 경로가 산정을 다시 돌리지 않아 근거가 여기밖에 없어서다 (P5)
        stgyExecLogService.log(StgyTyp.PTAWY, stgy.getId(), stgy.getLastRvsnNo(), TrgrTyp.PREVIEW,
                ibLine.getIbOrder().getIbNo(),
                "요청 " + result.reqQty() + " 중 배정 " + result.asgnQty()
                        + " (" + result.assignments().size() + "개 로케이션)",
                result.trace());

        return new PutawayBulkRecommendResponse.Item(item.ibLineId(), item.lotId(),
                prod.getProdCd(), prod.getProdNm(), true, stgy.getStgyNm(), stgy.getLastRvsnNo(),
                result.reqQty(), result.asgnQty(), result.remainQty(), assignments);
    }

    /** 관리자 미리보기 — 미저장 정의로 산정. DB 변경·로그 기록 없음 */
    public PutawayRecommendResponse preview(PtawyStgyDefinition definition, PtawyPreviewRequest request) {
        if (request.qty() == null || request.qty() < 1) {
            throw new IllegalArgumentException("미리보기 수량은 1 이상이어야 합니다.");
        }
        Prod prod;
        String vndrCd = null;
        if (request.ibLineId() != null) {
            IbLine ibLine = ibLineRepository.findById(request.ibLineId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + request.ibLineId()));
            prod = ibLine.getProd();
            vndrCd = ibLine.getIbOrder().getVendor().getVndrCd();
        } else if (request.prodId() != null) {
            prod = prodRepository.findById(request.prodId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + request.prodId()));
        } else {
            throw new IllegalArgumentException("미리보기 대상(입고 라인 또는 상품)이 없습니다.");
        }
        PutawayTarget target = new PutawayTarget(prod, vndrCd);

        // 편집 중 정의 그대로 산정 — 유형 매칭 선택이라 "실제 선택될 전략" 경고가 필요 없다.
        // 미리보기는 한 건이라 배치 간 공유가 없다
        return compute(definition, null, definition.stgyNm(), null, prod, target, request.qty(),
                locCapacityService.openInflowQtyByLoc(), new CrossBatch());
    }

    /** 전략 선택: 발주구분 일치 전략 → 전체(odr_dvsn IS NULL) 전략 → 없으면 수동 폴백. 유형당 1개라 결정적 */
    private Optional<PtawyStgy> selectStrategy(String odrDvsn) {
        return ptawyStgyRepository.findByOdrDvsn(odrDvsn)
                .or(ptawyStgyRepository::findByOdrDvsnIsNull);
    }

    /**
     * 추천 산정 본체 — 단계 순회 → 방식 후보 → 조건 필터 → 정렬 → 적재가능 계산·배정.
     *
     * @param inflowByLoc 로케이션별 미완료 지시 유입 잔량 (적치지시 + 이동지시). 적재가능에서 뺀다
     * @param crossBatch  같은 일괄 추천에서 앞선 배치가 이미 배정한 내역. 단건이면 비어 있다
     */
    private PutawayRecommendResponse compute(PtawyStgyDefinition def, Long stgyId, String stgyNm,
                                             Long rvsnNo, Prod prod, PutawayTarget target, long reqQty,
                                             Map<Long, Long> inflowByLoc, CrossBatch crossBatch) {
        // 후보 현황에 배치 간 배정을 먼저 얹는다 — 수량만 빼면 방식 판정이 앞 배치를 보지 못해
        // 빈로케이션이 이미 채우기로 한 곳을 빈 곳으로, 적재로케이션이 같은 상품을 넣기로 한 곳을
        // 남의 자리로 취급한다. 현황을 고쳐두면 방식·조건·적재가능이 같은 사실을 본다
        List<PutawayMethodContext.LocStock> stocks =
                putawayQueryRepository.storageStocks(prod.getTmpZon(), prod.getId()).stream()
                        .map(stock -> crossBatch.applyTo(stock, prod.getId()))
                        .toList();
        long unit = Boolean.TRUE.equals(def.untSpltYn()) ? unitOf(prod) : 1;

        long remaining = reqQty;
        Map<Long, PutawayRecommendResponse.Assignment> assignments = new LinkedHashMap<>();
        List<PutawayDecisionTrace.StageTrace> stageTraces = new ArrayList<>();

        List<PtawyStgyDefinition.StageDef> stages = def.stages().stream()
                .sorted(Comparator.comparing(s -> s.srtSeq() != null ? s.srtSeq() : 0))
                .toList();

        for (PtawyStgyDefinition.StageDef stage : stages) {
            if (remaining == 0) {
                stageTraces.add(stageTrace(stage, "SKIP — 잔여수량 없음", null));
                continue;
            }
            if (!ConditionEvaluator.matchesAll(stage.lineCond(), PutawayTargetField.BY_CODE, target)) {
                stageTraces.add(stageTrace(stage, "SKIP — 라인 조건 불일치", null));
                continue;
            }

            List<PutawayMethodContext.LocStock> candidates = PutawayMethod.of(stage.mthdCd())
                    .candidates(new PutawayMethodContext(prod, stocks))
                    .stream()
                    .filter(ls -> ConditionEvaluator.matchesAll(stage.locCond(), PutawayLocField.BY_CODE, ls))
                    .sorted(locComparator(def.locSrt()))
                    .toList();

            List<PutawayDecisionTrace.LocTrace> locTraces = new ArrayList<>();
            for (PutawayMethodContext.LocStock candidate : candidates) {
                if (remaining == 0) {
                    break;
                }
                Loc candidateLoc = candidate.loc();
                long assignedHere = assignments.containsKey(candidateLoc.getId())
                        ? assignments.get(candidateLoc.getId()).qty() : 0;
                long inflow = inflowByLoc.getOrDefault(candidateLoc.getId(), 0L);
                long crossHere = crossBatch.qtyOf(candidateLoc.getId());
                // 적재가능 = max_qty − 점유. 점유는 현재고 + 미완료 지시 유입 잔량(LocCapacityService와
                // 같은 식) + 이번 배치에서 이미 배정한 분. 앞선 배치 배정분은 occupiedQty에 이미 들어 있다.
                // max_qty NULL은 스키마상 STORAGE에 없어야 하지만(ck_loc_storage_capacity),
                // 라이브 불일치를 대비해 무제한으로 다루되 trace에 경고를 남긴다.
                long avail;
                if (candidateLoc.getMaxQty() == null) {
                    avail = remaining;
                } else {
                    avail = Math.max(0, candidateLoc.getMaxQty()
                            - candidate.occupiedQty() - inflow - assignedHere);
                }
                long assign = Math.min(avail, remaining);
                if (unit > 1) {
                    assign = assign / unit * unit;
                }

                locTraces.add(new PutawayDecisionTrace.LocTrace(
                        candidateLoc.getLocCd(), avail, assign,
                        inflow > 0 ? inflow : null, // 미완료 지시가 이미 잡아둔 자리
                        crossHere > 0 ? crossHere : null, // 앞선 배치가 잡아둔 자리
                        candidateLoc.getMaxQty() == null ? "최대 적재 수량 미설정 — 무제한으로 계산" : null,
                        assign == 0
                                ? (avail == 0 ? "적재 가능 수량 없음" : "입수 단위(" + unit + ") 미만")
                                : null));

                if (assign > 0) {
                    long total = assignedHere + assign;
                    assignments.put(candidateLoc.getId(), new PutawayRecommendResponse.Assignment(
                            candidateLoc.getId(), candidateLoc.getLocCd(), total));
                    remaining -= assign;
                }
            }
            stageTraces.add(stageTrace(stage, "PASS", locTraces));
        }

        long assigned = reqQty - remaining;
        return new PutawayRecommendResponse(true, stgyId, stgyNm, rvsnNo, reqQty, assigned, remaining,
                List.copyOf(assignments.values()),
                new PutawayDecisionTrace(reqQty, assigned, stageTraces));
    }

    private static PutawayDecisionTrace.StageTrace stageTrace(PtawyStgyDefinition.StageDef stage,
                                                              String gate,
                                                              List<PutawayDecisionTrace.LocTrace> locs) {
        return new PutawayDecisionTrace.StageTrace(stage.srtSeq(), stage.mthdCd(), gate, locs);
    }

    /**
     * 같은 일괄 추천에서 앞선 배치가 이미 배정한 내역 — 로케이션별 수량과 상품 집합.
     * <p>
     * 추천은 재고를 예약하지 않으므로 DB 현황은 배치를 넘겨도 그대로다. 그 상태로 배치마다
     * 산정하면 같은 자리를 여러 배치가 각자 다 쓸 수 있다고 본다. 그래서 배정할 때마다 여기
     * 누적하고 다음 배치의 후보 현황(LocStock)에 <b>보유수량과 같은 자격으로</b> 얹는다 —
     * 수량만 빼면 방식 판정(빈로케이션·적재로케이션)이 앞 배치를 보지 못하기 때문이다.
     */
    private static final class CrossBatch {

        private final Map<Long, Long> qtyByLoc = new LinkedHashMap<>();
        private final Map<Long, Set<Long>> prodIdsByLoc = new LinkedHashMap<>();

        void add(Long locId, Long prodId, long qty) {
            qtyByLoc.merge(locId, qty, Long::sum);
            prodIdsByLoc.computeIfAbsent(locId, key -> new HashSet<>()).add(prodId);
        }

        long qtyOf(Long locId) {
            return qtyByLoc.getOrDefault(locId, 0L);
        }

        /** 후보 현황 1건에 이번 호출의 배정을 얹은 사본. 배정이 없으면 원본 그대로 */
        PutawayMethodContext.LocStock applyTo(PutawayMethodContext.LocStock stock, Long prodId) {
            long qty = qtyOf(stock.loc().getId());
            if (qty == 0) {
                return stock;
            }
            boolean hasProd = stock.hasProd()
                    || prodIdsByLoc.getOrDefault(stock.loc().getId(), Set.of()).contains(prodId);
            return new PutawayMethodContext.LocStock(
                    stock.loc(), stock.occupiedQty() + qty, hasProd, stock.bizDvsn());
        }
    }

    /** 입수 = ea_qty(입고단위). 재고 수량이 낱개(EA)라 입고단위 낱개수량이 곧 배수다 */
    private long unitOf(Prod prod) {
        return Math.max(prod.eaQtyOf(prod.getInbUomCd()), 1);
    }

    /** 후보 정렬. 빈 목록 = 기본(피킹순위 ASC → 로케이션코드 ASC). 끝에 id를 붙여 항상 결정적 */
    private Comparator<PutawayMethodContext.LocStock> locComparator(List<SortCriterion> criteria) {
        List<SortCriterion> effective = criteria == null || criteria.isEmpty()
                ? List.of(new SortCriterion(PutawaySortField.PIKNG_PRTY.name(), "ASC"),
                        new SortCriterion(PutawaySortField.LOC_CD.name(), "ASC"))
                : criteria;
        Comparator<PutawayMethodContext.LocStock> comparator = null;
        for (SortCriterion criterion : effective) {
            Comparator<PutawayMethodContext.LocStock> one =
                    PutawaySortField.of(criterion.field()).comparator(criterion.asc());
            comparator = comparator == null ? one : comparator.thenComparing(one);
        }
        return comparator.thenComparing(ls -> ls.loc().getId());
    }
}
