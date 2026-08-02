package com.project.wmsback.strategy.inspection.service;

import com.project.wmsback.inbound.dto.ReceiveRequest;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import com.project.wmsback.strategy.inspection.dto.InspPlcyDefinition;
import com.project.wmsback.strategy.inspection.dto.InspRuleResult;
import com.project.wmsback.strategy.inspection.entity.InspPlcy;
import com.project.wmsback.strategy.inspection.exception.InspectionViolationException;
import com.project.wmsback.strategy.inspection.repository.InspPlcyRepository;
import com.project.wmsback.strategy.inspection.repository.InspectionQueryRepository;
import com.project.wmsback.strategy.inspection.rule.InspectionContext;
import com.project.wmsback.strategy.inspection.rule.InspectionRule;
import com.project.wmsback.strategy.inspection.rule.Violation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        List<InspectionViolationException.LineViolation> violations = new ArrayList<>();
        List<Map<String, Object>> trace = new ArrayList<>();

        for (ReceiveRequest.Line line : lines) {
            IbLine ibLine = ibLineRepository.findById(line.getIbLineId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고 라인입니다: " + line.getIbLineId()));
            Prod prod = ibLine.getProd();
            LocalDate receiptDt = line.getReceiptDt() != null ? line.getReceiptDt() : LocalDate.now();

            List<InspRuleResult> results = evaluateOne(ruleDefs, prod, receiptDt, line.getMfgDt());
            results.stream()
                    .filter(r -> !r.pass())
                    .forEach(r -> violations.add(new InspectionViolationException.LineViolation(
                            ibLine.getId(), prod.getProdCd(), r.ruleCd(), r.ruleName(),
                            r.message(), r.actual(), r.expected())));

            Map<String, Object> lineTrace = new LinkedHashMap<>();
            lineTrace.put("ibLineId", ibLine.getId());
            lineTrace.put("prodCd", prod.getProdCd());
            lineTrace.put("rules", results);
            trace.add(lineTrace);
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
}
