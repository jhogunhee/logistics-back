package com.project.wmsback.strategy.inspection.service;

import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.master.repository.ProdRepository;
import com.project.wmsback.strategy.core.dto.RvsnResponse;
import com.project.wmsback.strategy.core.entity.StgyTyp;
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

    /** 정책 삭제 (물리삭제). 리비전·실행 로그는 감사용으로 남는다 (조회 전용 — 복원 흐름 없음) */
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

    /** 현재 정책의 리비전 이력 — 정책이 없으면(삭제 포함) 빈 목록 */
    public List<RvsnResponse> revisions() {
        return inspPlcyRepository.findFirstByOrderByIdAsc()
                .map(plcy -> stgyRvsnService.list(StgyTyp.INSP, plcy.getId()))
                .orElseGet(List::of);
    }

    public JsonNode revision(Long rvsnNo) {
        return stgyRvsnService.snapshotTree(StgyTyp.INSP, loadPolicy().getId(), rvsnNo);
    }

    private InspPlcy loadPolicy() {
        return inspPlcyRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalArgumentException("검수 정책이 없습니다 — 먼저 생성하세요."));
    }

    /**
     * 저장 검증 (P2): 정책명 필수, rule_cd 실존(InspectionRule enum) + deprecated 금지 + 중복 금지,
     * 파라미터는 규칙별 validatePara로 검증·정규화.
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
            InspectionRule rule = InspectionRule.find(def.ruleCd())
                    .orElseThrow(() -> new IllegalArgumentException("없는 검수 규칙입니다: " + def.ruleCd()));
            if (rule.deprecated()) {
                throw new IllegalArgumentException("은퇴한 규칙은 새로 등록할 수 없습니다: " + rule.label());
            }
            if (!seen.add(def.ruleCd())) {
                throw new IllegalArgumentException("같은 규칙을 두 번 등록할 수 없습니다: " + rule.label());
            }
            Map<String, Object> para = rule.validatePara(def.para());
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
