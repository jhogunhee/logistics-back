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
import com.project.wmsback.strategy.putaway.dto.PtawyPreviewRequest;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyDefinition;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyResponse;
import com.project.wmsback.strategy.putaway.dto.PutawayRecommendRequest;
import com.project.wmsback.strategy.putaway.dto.PutawayRecommendResponse;
import com.project.wmsback.strategy.putaway.entity.PtawyStgy;
import com.project.wmsback.strategy.putaway.field.PutawayLocField;
import com.project.wmsback.strategy.putaway.field.PutawayTarget;
import com.project.wmsback.strategy.putaway.field.PutawayTargetField;
import com.project.wmsback.strategy.putaway.method.PutawayMethod;
import com.project.wmsback.strategy.putaway.method.PutawayMethodContext;
import com.project.wmsback.strategy.putaway.repository.PtawyStgyRepository;
import com.project.wmsback.strategy.putaway.repository.PutawayQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 적치 추천 산정. 전략은 추천만 한다 — 실행(즉시 MOVE)은 기존 PutawayService가 담당하고,
 * 추천과 실행 사이의 재고 변동은 실행 측 가드가 최종 방어한다 (추천은 예약이 아니다).
 * 작업자 추천(recommend)과 관리자 미리보기(preview)가 같은 산정 함수(compute)를 공유한다 (P4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PutawayRecommendService {

    private final PtawyStgyRepository ptawyStgyRepository;
    private final IbLineRepository ibLineRepository;
    private final ProdRepository prodRepository;
    private final PutawayQueryRepository putawayQueryRepository;
    private final StgyExecLogService stgyExecLogService;

    /** 작업자 추천 — 전략 미설정이면 strategySelected=false로 응답하고 화면이 수동 후보로 폴백 */
    public PutawayRecommendResponse recommend(Long ibLineId, PutawayRecommendRequest request) {
        if (request.qty() == null || request.qty() < 1) {
            throw new IllegalArgumentException("추천할 수량은 1 이상이어야 합니다.");
        }
        IbLine ibLine = ibLineRepository.findById(ibLineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + ibLineId));
        Prod prod = ibLine.getProd();
        PutawayTarget target = new PutawayTarget(prod, ibLine.getIbOrder().getVendor().getVndrCd());

        Optional<PtawyStgy> selected = selectStrategy(ibLine.getIbOrder().getOdrDvsn());
        if (selected.isEmpty()) {
            return PutawayRecommendResponse.noStrategy(request.qty());
        }
        PtawyStgy stgy = selected.get();
        PutawayRecommendResponse result = compute(PtawyStgyResponse.from(stgy).toDefinition(),
                stgy.getId(), stgy.getStgyNm(), stgy.getLastRvsnNo(), prod, target, request.qty());

        stgyExecLogService.log(StgyTyp.PTAWY, stgy.getId(), stgy.getLastRvsnNo(), TrgrTyp.MANUAL,
                ibLine.getIbOrder().getIbNo(),
                "요청 " + result.reqQty() + " 중 배정 " + result.asgnQty()
                        + " (" + result.assignments().size() + "개 로케이션)",
                result.trace());
        return result;
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

        // 편집 중 정의 그대로 산정 — 유형 매칭 선택이라 "실제 선택될 전략" 경고가 필요 없다
        return compute(definition, null, definition.stgyNm(), null, prod, target, request.qty());
    }

    /** 전략 선택: 발주구분 일치 전략 → 전체(odr_dvsn IS NULL) 전략 → 없으면 수동 폴백. 유형당 1개라 결정적 */
    private Optional<PtawyStgy> selectStrategy(String odrDvsn) {
        return ptawyStgyRepository.findByOdrDvsn(odrDvsn)
                .or(ptawyStgyRepository::findByOdrDvsnIsNull);
    }

    /**
     * 추천 산정 본체 — 단계 순회 → 방식 후보 → 조건 필터 → 정렬 → 적재가능 계산·배정.
     */
    private PutawayRecommendResponse compute(PtawyStgyDefinition def, Long stgyId, String stgyNm,
                                             Long rvsnNo, Prod prod, PutawayTarget target, long reqQty) {
        List<PutawayMethodContext.LocStock> stocks =
                putawayQueryRepository.storageStocks(prod.getTmpZon(), prod.getId());
        long unit = Boolean.TRUE.equals(def.untSpltYn()) ? unitOf(prod) : 1;

        long remaining = reqQty;
        Map<Long, PutawayRecommendResponse.Assignment> assignments = new LinkedHashMap<>();
        List<Map<String, Object>> stageTraces = new ArrayList<>();

        List<PtawyStgyDefinition.StageDef> stages = def.stages().stream()
                .sorted(Comparator.comparing(s -> s.srtSeq() != null ? s.srtSeq() : 0))
                .toList();

        for (PtawyStgyDefinition.StageDef stage : stages) {
            Map<String, Object> stageTrace = new LinkedHashMap<>();
            stageTrace.put("srtSeq", stage.srtSeq());
            stageTrace.put("mthdCd", stage.mthdCd());
            stageTraces.add(stageTrace);

            if (remaining == 0) {
                stageTrace.put("gate", "SKIP — 잔여수량 없음");
                continue;
            }
            if (!ConditionEvaluator.matchesAll(stage.lineCond(), PutawayTargetField.BY_CODE, target)) {
                stageTrace.put("gate", "SKIP — 라인 조건 불일치");
                continue;
            }
            stageTrace.put("gate", "PASS");

            List<PutawayMethodContext.LocStock> candidates = PutawayMethod.of(stage.mthdCd())
                    .candidates(new PutawayMethodContext(prod, stocks))
                    .stream()
                    .filter(ls -> ConditionEvaluator.matchesAll(stage.locCond(), PutawayLocField.BY_CODE, ls))
                    .sorted(locComparator(def.locSrt()))
                    .toList();

            List<Map<String, Object>> locTraces = new ArrayList<>();
            stageTrace.put("locs", locTraces);

            for (PutawayMethodContext.LocStock candidate : candidates) {
                if (remaining == 0) {
                    break;
                }
                Loc candidateLoc = candidate.loc();
                long assignedHere = assignments.containsKey(candidateLoc.getId())
                        ? assignments.get(candidateLoc.getId()).qty() : 0;
                // 적재가능 = max_qty − 점유(현재고 + 이번 추천에서 이미 배정한 분).
                // max_qty NULL은 스키마상 STORAGE에 없어야 하지만(ck_loc_storage_capacity),
                // 라이브 불일치를 대비해 무제한으로 다루되 trace에 경고를 남긴다.
                long avail;
                if (candidateLoc.getMaxQty() == null) {
                    avail = remaining;
                } else {
                    avail = Math.max(0, candidateLoc.getMaxQty() - candidate.occupiedQty() - assignedHere);
                }
                long assign = Math.min(avail, remaining);
                if (unit > 1) {
                    assign = assign / unit * unit;
                }

                Map<String, Object> locTrace = new LinkedHashMap<>();
                locTrace.put("locCd", candidateLoc.getLocCd());
                locTrace.put("avalQty", avail);
                locTrace.put("asgnQty", assign);
                if (candidateLoc.getMaxQty() == null) {
                    locTrace.put("warn", "최대 적재 수량 미설정 — 무제한으로 계산");
                }
                if (assign == 0) {
                    locTrace.put("skip", avail == 0 ? "적재 가능 수량 없음" : "입수 단위(" + unit + ") 미만");
                }
                locTraces.add(locTrace);

                if (assign > 0) {
                    long total = assignedHere + assign;
                    assignments.put(candidateLoc.getId(), new PutawayRecommendResponse.Assignment(
                            candidateLoc.getId(), candidateLoc.getLocCd(), total));
                    remaining -= assign;
                }
            }
        }

        long assigned = reqQty - remaining;
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("reqQty", reqQty);
        trace.put("asgnQty", assigned);
        trace.put("stages", stageTraces);

        return new PutawayRecommendResponse(true, stgyId, stgyNm, rvsnNo, reqQty, assigned, remaining,
                List.copyOf(assignments.values()), trace);
    }

    /** 입수 = ea_qty(입고단위). 재고 수량이 낱개(EA)라 입고단위 낱개수량이 곧 배수다 */
    private long unitOf(Prod prod) {
        return Math.max(prod.eaQtyOf(prod.getInbUomCd()), 1);
    }

    /** 후보 정렬. 빈 목록 = 기본(피킹순위 ASC → 로케이션코드 ASC). 끝에 id를 붙여 항상 결정적 */
    private Comparator<PutawayMethodContext.LocStock> locComparator(List<SortCriterion> criteria) {
        List<SortCriterion> effective = criteria == null || criteria.isEmpty()
                ? List.of(new SortCriterion("PIKNG_PRTY", "ASC"), new SortCriterion("LOC_CD", "ASC"))
                : criteria;
        Comparator<PutawayMethodContext.LocStock> comparator = null;
        for (SortCriterion criterion : effective) {
            Comparator<PutawayMethodContext.LocStock> one = switch (criterion.field()) {
                case "PIKNG_PRTY" -> Comparator.comparing(ls -> ls.loc().getPikngPrty());
                case "PTAWY_PRTY" -> Comparator.comparing(ls -> ls.loc().getPtawyPrty());
                case "LOC_CD" -> Comparator.comparing(ls -> ls.loc().getLocCd());
                default -> throw new IllegalStateException("저장된 정렬 기준이 배포본과 어긋납니다: " + criterion.field());
            };
            if (!criterion.asc()) {
                one = one.reversed();
            }
            comparator = comparator == null ? one : comparator.thenComparing(one);
        }
        return comparator.thenComparing(ls -> ls.loc().getId());
    }
}
