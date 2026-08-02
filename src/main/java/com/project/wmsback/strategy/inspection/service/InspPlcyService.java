package com.project.wmsback.strategy.inspection.service;

import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.master.repository.ProdRepository;
import com.project.wmsback.strategy.core.dto.RvsnResponse;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.param.ParamValidator;
import com.project.wmsback.strategy.core.registry.StrategyComponentRegistry;
import com.project.wmsback.strategy.core.service.StgyRvsnService;
import com.project.wmsback.strategy.inspection.dto.InspPlcyDefinition;
import com.project.wmsback.strategy.inspection.dto.InspPlcyResponse;
import com.project.wmsback.strategy.inspection.dto.InspPreviewRequest;
import com.project.wmsback.strategy.inspection.dto.InspPreviewResponse;
import com.project.wmsback.strategy.inspection.entity.InspPlcy;
import com.project.wmsback.strategy.inspection.entity.InspPlcyRule;
import com.project.wmsback.strategy.inspection.repository.InspPlcyRepository;
import com.project.wmsback.strategy.inspection.rule.InspectionRule;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 검수 정책 관리 (CRUD·미리보기·리비전). 저장 검증이 P2의 관문이다 —
 * 여기서 거부되면 "등록은 되는데 실행하면 오류"인 상태가 생기지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspPlcyService {

    private final InspPlcyRepository inspPlcyRepository;
    private final ProdRepository prodRepository;
    private final StrategyComponentRegistry registry;
    private final StgyRvsnService stgyRvsnService;
    private final InspectionService inspectionService;

    public InspPlcyResponse get() {
        return inspPlcyRepository.findFirstByOrderByIdAsc()
                .map(InspPlcyResponse::from)
                .orElseGet(InspPlcyResponse::empty);
    }

    /** 정책 생성. 전역 1행 — 기존 행이 있으면 거부한다 (D8: 서비스 검증) */
    @Transactional
    public InspPlcyResponse create(InspPlcyDefinition definition) {
        if (inspPlcyRepository.count() > 0) {
            throw new IllegalStateException("검수 정책은 하나만 만들 수 있습니다 — 기존 정책을 수정하세요.");
        }
        InspPlcyDefinition normalized = validate(definition);
        InspPlcy plcy = InspPlcy.builder().stgyNm(normalized.stgyNm()).build();
        toRules(normalized).forEach(plcy::addRule);
        inspPlcyRepository.save(plcy);
        stgyRvsnService.snapshot(StgyTyp.INSP, plcy.getId(), plcy.getLastRvsnNo(), normalized);
        return InspPlcyResponse.from(plcy);
    }

    /** 정책 수정 — 규칙 목록 통째 교체 + 리비전 증가 + 스냅샷 (한 트랜잭션) */
    @Transactional
    public InspPlcyResponse update(InspPlcyDefinition definition) {
        InspPlcy plcy = loadPolicy();
        InspPlcyDefinition normalized = validate(definition);
        long rvsnNo = plcy.applyDefinition(normalized.stgyNm(), toRules(normalized));
        stgyRvsnService.snapshot(StgyTyp.INSP, plcy.getId(), rvsnNo, normalized);
        return InspPlcyResponse.from(plcy);
    }

    /** 정책 삭제 (물리삭제 — D4). 리비전·실행 로그는 남는다 */
    @Transactional
    public void delete() {
        inspPlcyRepository.delete(loadPolicy());
    }

    /** 미리보기 — 미저장 정의를 검증 후 로트들에 평가. DB 변경·로그 기록 없음 */
    public InspPreviewResponse preview(InspPreviewRequest request) {
        if (request.definition() == null) {
            throw new IllegalArgumentException("미리보기할 정의가 없습니다.");
        }
        if (request.lots() == null || request.lots().isEmpty()) {
            throw new IllegalArgumentException("미리보기 대상 로트가 없습니다.");
        }
        InspPlcyDefinition normalized = validate(request.definition());
        List<InspPreviewResponse.LotResult> results = new ArrayList<>();
        for (InspPreviewRequest.PreviewLot lot : request.lots()) {
            Prod prod = prodRepository.findById(lot.prodId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + lot.prodId()));
            LocalDate receiptDt = lot.receiptDt() != null ? lot.receiptDt() : LocalDate.now();
            results.add(new InspPreviewResponse.LotResult(prod.getId(), prod.getProdCd(), prod.getProdNm(),
                    inspectionService.evaluateOne(normalized.rules(), prod, receiptDt, lot.mfgDt())));
        }
        return new InspPreviewResponse(results);
    }

    /**
     * 이력·복원의 기준 stgy_id. 정책이 살아 있으면 그 id, 삭제됐으면 stgy_rvsn에 남은
     * 가장 최근 정책의 id — 삭제 후에도 이력 조회·복원이 가능해야 한다 (D4의 안전망).
     * 여러 번 삭제·재생성했다면 마지막 정책의 이력을 가리킨다.
     */
    private java.util.Optional<Long> anchorStgyId() {
        return inspPlcyRepository.findFirstByOrderByIdAsc().map(InspPlcy::getId)
                .or(() -> stgyRvsnService.latestPerStrategy(StgyTyp.INSP).stream()
                        .findFirst().map(r -> r.getStgyId()));
    }

    public List<RvsnResponse> revisions() {
        return anchorStgyId()
                .map(id -> stgyRvsnService.list(StgyTyp.INSP, id))
                .orElseGet(List::of);
    }

    public JsonNode revision(Long rvsnNo) {
        Long anchor = anchorStgyId()
                .orElseThrow(() -> new IllegalArgumentException("리비전 이력이 없습니다."));
        return stgyRvsnService.snapshotTree(StgyTyp.INSP, anchor, rvsnNo);
    }

    /**
     * 복원 = 스냅샷을 새 저장으로 재생 (검증 포함, 리비전은 앞으로만 증가).
     * 정책이 삭제된 상태면 스냅샷으로 새 정책을 생성한다 (프로세스정의서 §4.3).
     */
    @Transactional
    public InspPlcyResponse restore(Long rvsnNo) {
        Long anchor = anchorStgyId()
                .orElseThrow(() -> new IllegalArgumentException("리비전 이력이 없습니다."));
        InspPlcyDefinition snapshot = stgyRvsnService.snapshotAs(
                StgyTyp.INSP, anchor, rvsnNo, InspPlcyDefinition.class);
        return inspPlcyRepository.findFirstByOrderByIdAsc().isPresent()
                ? update(snapshot)
                : create(snapshot);
    }

    private InspPlcy loadPolicy() {
        return inspPlcyRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalArgumentException("검수 정책이 없습니다 — 먼저 생성하세요."));
    }

    /**
     * 저장 검증 (P2): 정책명 필수, rule_cd 레지스트리 실존 + deprecated 금지 + 중복 금지,
     * 파라미터는 ParamSpec 스키마로 검증·정규화.
     */
    private InspPlcyDefinition validate(InspPlcyDefinition definition) {
        if (definition.stgyNm() == null || definition.stgyNm().isBlank()) {
            throw new IllegalArgumentException("정책명은 필수입니다.");
        }
        List<InspPlcyDefinition.RuleDef> rules = definition.rules() != null ? definition.rules() : List.of();
        Set<String> seen = new HashSet<>();
        List<InspPlcyDefinition.RuleDef> normalized = new ArrayList<>();
        int seq = 0;
        for (InspPlcyDefinition.RuleDef def : rules) {
            InspectionRule rule = registry.find(InspectionRule.class, def.ruleCd())
                    .orElseThrow(() -> new IllegalArgumentException("없는 검수 규칙입니다: " + def.ruleCd()));
            if (rule.descriptor().deprecated()) {
                throw new IllegalArgumentException("은퇴한 규칙은 새로 등록할 수 없습니다: " + rule.descriptor().name());
            }
            if (!seen.add(def.ruleCd())) {
                throw new IllegalArgumentException("같은 규칙을 두 번 등록할 수 없습니다: " + rule.descriptor().name());
            }
            Map<String, Object> para = ParamValidator.validate(
                    rule.descriptor().name(), rule.descriptor().params(), def.para());
            normalized.add(new InspPlcyDefinition.RuleDef(
                    def.srtSeq() != null ? def.srtSeq() : seq, def.ruleCd(), para));
            seq++;
        }
        return new InspPlcyDefinition(definition.stgyNm(), normalized);
    }

    private List<InspPlcyRule> toRules(InspPlcyDefinition definition) {
        return definition.rules().stream()
                .map(d -> InspPlcyRule.builder().srtSeq(d.srtSeq()).ruleCd(d.ruleCd()).para(d.para()).build())
                .toList();
    }
}
