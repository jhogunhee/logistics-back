package com.project.wmsback.strategy.inspection.service;

import com.project.wmsback.inbound.dto.ReceiveRequest;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import com.project.wmsback.strategy.inspection.dto.InspLineTrace;
import com.project.wmsback.strategy.inspection.dto.InspMinMfgDtRequest;
import com.project.wmsback.strategy.inspection.dto.InspMinMfgDtResponse;
import com.project.wmsback.strategy.inspection.dto.InspPlcyDefinition;
import com.project.wmsback.strategy.inspection.dto.InspRuleResult;
import com.project.wmsback.strategy.inspection.entity.InspPlcy;
import com.project.wmsback.strategy.inspection.exception.InspectionViolationException;
import com.project.wmsback.strategy.inspection.repository.InspPlcyRepository;
import com.project.wmsback.strategy.inspection.repository.InspectionQueryRepository;
import com.project.wmsback.strategy.inspection.component.InspectionContext;
import com.project.wmsback.strategy.inspection.component.InspectionRule;
import com.project.wmsback.strategy.inspection.component.Violation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 검수 제약 실행. 검수 저장(ReceivingService.receive) 직전에 정책의 규칙 전부를
 * 라인별 AND 평가하고, 위반이 하나라도 있으면 예외로 저장 전체를 거부한다.
 * 실행·미리보기가 같은 평가 함수(evaluateOne)를 공유한다 (P4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionService {

    private final InspPlcyRepository inspPlcyRepository;
    private final IbLineRepository ibLineRepository;
    private final InspectionQueryRepository inspectionQueryRepository;
    private final StgyExecLogService stgyExecLogService;
    private final ProdRepository prodRepository;

    /**
     * 검수 저장 직전 훅. 정책이 없거나 규칙이 0건이면 제약 없이 통과 (검수 자체를 막지 않는다).
     * 위반·통과 모두 실행 로그를 남긴다 — 로그는 REQUIRES_NEW라 위반 롤백에도 살아남는다.
     */
    public void checkReceive(IbOrder order, List<ReceiveRequest.Line> lines) {
        Optional<InspPlcy> found = inspPlcyRepository.findFirstByOrderByIdAsc();
        if (found.isEmpty() || found.get().getRules().isEmpty()) {
            return;
        }
        InspPlcy plcy = found.get();
        List<InspPlcyDefinition.RuleDef> ruleDefs = plcy.getRules().stream()
                .map(r -> new InspPlcyDefinition.RuleDef(r.getSrtSeq(), r.getRuleCd(), r.getPara()))
                .toList();

        Map<Long, IbLine> ibLines = loadLines(order, lines);
        List<InspectionViolationException.LineViolation> violations = new ArrayList<>();
        List<InspLineTrace> trace = new ArrayList<>();

        for (ReceiveRequest.Line line : lines) {
            IbLine ibLine = ibLines.get(line.getIbLineId());
            Prod prod = ibLine.getProd();
            LocalDate receiptDt = line.getReceiptDt() != null ? line.getReceiptDt() : LocalDate.now();

            List<InspRuleResult> results = evaluateOne(ruleDefs, prod, receiptDt, line.getMfgDt());
            results.stream()
                    .filter(r -> !r.pass())
                    .forEach(r -> violations.add(new InspectionViolationException.LineViolation(
                            ibLine.getId(), prod.getProdCd(), r.ruleCd(), r.ruleName(),
                            r.message(), r.actual(), r.expected())));

            trace.add(new InspLineTrace(ibLine.getId(), prod.getProdCd(), results));
        }

        stgyExecLogService.log(StgyTyp.INSP, plcy.getId(), plcy.getLastRvsnNo(), TrgrTyp.MANUAL,
                order.getIbNo(),
                "라인 " + lines.size() + "건 중 위반 " + violations.size() + "건",
                trace);

        if (!violations.isEmpty()) {
            throw new InspectionViolationException(violations);
        }
    }

    /**
     * 요청 라인을 한 번에 읽고 <b>이 입고의 라인인지</b>까지 확인한다. 남의 라인을 그대로 판정하면
     * 저장 단계(ReceivingService#findLine)가 거부하기 전에 그 라인의 판정이 실행 로그에 남는다 —
     * 로그는 REQUIRES_NEW라 거부 롤백에도 살아남기 때문이다.
     */
    private Map<Long, IbLine> loadLines(IbOrder order, List<ReceiveRequest.Line> lines) {
        List<Long> ids = lines.stream()
                .map(ReceiveRequest.Line::getIbLineId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, IbLine> byId = ids.isEmpty() ? Map.of()
                : ibLineRepository.findAllWithProdAndOrderByIdIn(ids).stream()
                        .collect(Collectors.toMap(IbLine::getId, Function.identity()));
        for (ReceiveRequest.Line line : lines) {
            IbLine ibLine = byId.get(line.getIbLineId());
            if (ibLine == null) {
                throw new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + line.getIbLineId());
            }
            if (!ibLine.getIbOrder().getId().equals(order.getId())) {
                throw new IllegalArgumentException("다른 입고의 라인입니다: " + line.getIbLineId());
            }
        }
        return byId;
    }

    /**
     * 규칙 목록 하나를 (상품, 입고일자, 제조일자)에 평가. 실행·미리보기 공용 —
     * 저장 검증(P2)을 통과한 정의를 전제하므로 미등록 rule_cd는 여기서 IllegalStateException이다.
     */
    public List<InspRuleResult> evaluateOne(List<InspPlcyDefinition.RuleDef> ruleDefs,
                                            Prod prod, LocalDate receiptDt, LocalDate mfgDt) {
        InspectionContext ctx = new InspectionContext(prod, receiptDt, mfgDt, inspectionQueryRepository);
        List<InspRuleResult> results = new ArrayList<>();
        for (InspPlcyDefinition.RuleDef def : ruleDefs) {
            InspectionRule rule = InspectionRule.of(def.ruleCd());
            String ruleName = rule.label();

            Optional<String> skip = rule.skipReason(ctx);
            if (skip.isPresent()) {
                results.add(InspRuleResult.skip(def.ruleCd(), ruleName, skip.get()));
                continue;
            }
            Optional<Violation> violation = rule.check(ctx, def.para());
            results.add(violation
                    .map(v -> InspRuleResult.violation(def.ruleCd(), ruleName, v.message(), v.actual(), v.expected()))
                    .orElseGet(() -> InspRuleResult.pass(def.ruleCd(), ruleName)));
        }
        return results;
    }

    /**
     * 검수 입력 전 힌트 — 상품·입고일자마다 규칙별 「입고 가능한 가장 이른 제조일자」와 그중 가장 늦은 날(전체 하한).
     * 저장본 정책을 읽기만 한다 — 실행 로그를 남기지 않는다(판정이 아니라 안내다). 정책이 없으면 하한 없음.
     * 검수 화면이 제조일자를 치기 전에 "어디까지 되는지"를 보여주는 데 쓴다 — 거부당한 뒤에야 기준을 알면
     * 소급 등록·테스트 입력에서 날짜를 고르는 일이 헛걸음이 된다.
     */
    public InspMinMfgDtResponse minMfgDts(InspMinMfgDtRequest request) {
        List<InspMinMfgDtRequest.Item> items = request.items() != null ? request.items() : List.of();
        List<InspPlcyDefinition.RuleDef> ruleDefs = inspPlcyRepository.findFirstByOrderByIdAsc()
                .map(plcy -> plcy.getRules().stream()
                        .map(r -> new InspPlcyDefinition.RuleDef(r.getSrtSeq(), r.getRuleCd(), r.getPara()))
                        .toList())
                .orElse(List.of());

        List<Long> prodIds = items.stream().map(InspMinMfgDtRequest.Item::prodId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Prod> prods = prodIds.isEmpty() ? Map.of()
                : prodRepository.findAllById(prodIds).stream().collect(Collectors.toMap(Prod::getId, Function.identity()));

        List<InspMinMfgDtResponse.Item> result = new ArrayList<>();
        for (InspMinMfgDtRequest.Item item : items) {
            Prod prod = prods.get(item.prodId());
            if (prod == null) {
                throw new IllegalArgumentException("존재하지 않는 상품입니다: " + item.prodId());
            }
            LocalDate receiptDt = item.receiptDt() != null ? item.receiptDt() : LocalDate.now();
            InspectionContext ctx = new InspectionContext(prod, receiptDt, null, inspectionQueryRepository);

            List<InspMinMfgDtResponse.RuleMin> ruleMins = new ArrayList<>();
            LocalDate overall = null;
            for (InspPlcyDefinition.RuleDef def : ruleDefs) {
                InspectionRule rule = InspectionRule.of(def.ruleCd());
                LocalDate min = rule.minMfgDt(ctx, def.para()).orElse(null);
                ruleMins.add(new InspMinMfgDtResponse.RuleMin(def.ruleCd(), rule.label(), min));
                if (min != null && (overall == null || min.isAfter(overall))) {
                    overall = min;
                }
            }
            result.add(new InspMinMfgDtResponse.Item(prod.getId(), receiptDt, overall, ruleMins));
        }
        return new InspMinMfgDtResponse(result);
    }
}
