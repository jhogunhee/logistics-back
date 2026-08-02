package com.project.wmsback.strategy.putaway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.wmsback.strategy.core.condition.ConditionEvaluator;
import com.project.wmsback.strategy.core.condition.SortCriterion;
import com.project.wmsback.strategy.core.dto.RvsnResponse;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.param.ParamValidator;
import com.project.wmsback.strategy.core.registry.StrategyComponentRegistry;
import com.project.wmsback.strategy.core.service.StgyRvsnService;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyDefinition;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyDeletedResponse;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyResponse;
import com.project.wmsback.strategy.putaway.dto.PtawyStgySummaryResponse;
import com.project.wmsback.strategy.putaway.entity.PtawyStgy;
import com.project.wmsback.strategy.putaway.entity.PtawyStgyStg;
import com.project.wmsback.strategy.putaway.field.PutawayLocField;
import com.project.wmsback.strategy.putaway.field.PutawayTargetField;
import com.project.wmsback.strategy.putaway.method.PutawayMethod;
import com.project.wmsback.strategy.putaway.repository.PtawyStgyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 적치 전략 관리 (CRUD·리비전). 저장 검증이 P2의 관문 — 실행 불가 정의는 저장되지 않는다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PtawyStgyService {

    private static final Set<String> SORT_FIELDS = Set.of("PIKNG_PRTY", "PTAWY_PRTY", "LOC_CD");

    private final PtawyStgyRepository ptawyStgyRepository;
    private final StrategyComponentRegistry registry;
    private final StgyRvsnService stgyRvsnService;

    public List<PtawyStgySummaryResponse> list() {
        return ptawyStgyRepository.findAll(Sort.by("prty", "id")).stream()
                .map(PtawyStgySummaryResponse::from)
                .toList();
    }

    public PtawyStgyResponse get(Long id) {
        return PtawyStgyResponse.from(load(id));
    }

    @Transactional
    public PtawyStgyResponse create(PtawyStgyDefinition definition) {
        PtawyStgyDefinition normalized = validate(definition);
        PtawyStgy stgy = PtawyStgy.builder()
                .stgyNm(normalized.stgyNm())
                .prty(normalized.prty())
                .tgtCond(normalized.tgtCond())
                .untSpltYn(normalized.untSpltYn())
                .locSrt(normalized.locSrt())
                .build();
        toStages(normalized).forEach(stgy::addStage);
        ptawyStgyRepository.save(stgy);
        stgyRvsnService.snapshot(StgyTyp.PTAWY, stgy.getId(), stgy.getLastRvsnNo(), normalized);
        return PtawyStgyResponse.from(stgy);
    }

    @Transactional
    public PtawyStgyResponse update(Long id, PtawyStgyDefinition definition) {
        PtawyStgy stgy = load(id);
        PtawyStgyDefinition normalized = validate(definition);
        long rvsnNo = stgy.applyDefinition(normalized.stgyNm(), normalized.prty(), normalized.tgtCond(),
                normalized.untSpltYn(), normalized.locSrt(), toStages(normalized));
        stgyRvsnService.snapshot(StgyTyp.PTAWY, stgy.getId(), rvsnNo, normalized);
        return PtawyStgyResponse.from(stgy);
    }

    /** 물리삭제 (D4). 리비전·실행 로그는 남아 복원·감사가 가능하다 */
    @Transactional
    public void delete(Long id) {
        ptawyStgyRepository.delete(load(id));
    }

    /** 헤더 존재를 요구하지 않는다 — 삭제된 전략의 이력도 조회 가능해야 한다 (D4의 안전망) */
    public List<RvsnResponse> revisions(Long id) {
        return stgyRvsnService.list(StgyTyp.PTAWY, id);
    }

    public JsonNode revision(Long id, Long rvsnNo) {
        return stgyRvsnService.snapshotTree(StgyTyp.PTAWY, id, rvsnNo);
    }

    /**
     * 복원 = 스냅샷을 새 저장으로 재생 (검증 포함 — 은퇴 구성요소는 여기서 걸린다).
     * 전략이 삭제된 상태면 스냅샷으로 새 전략을 생성한다 — 새 id, 리비전 1부터 (프로세스정의서 §4.3).
     */
    @Transactional
    public PtawyStgyResponse restore(Long id, Long rvsnNo) {
        PtawyStgyDefinition snapshot = stgyRvsnService.snapshotAs(
                StgyTyp.PTAWY, id, rvsnNo, PtawyStgyDefinition.class);
        return ptawyStgyRepository.existsById(id)
                ? update(id, snapshot)
                : create(snapshot);
    }

    /** 삭제된 전략 목록 — stgy_rvsn에는 남았지만 헤더가 없는 것 (화면의 "삭제된 전략 복원" 진입점) */
    public List<PtawyStgyDeletedResponse> deleted() {
        Set<Long> alive = ptawyStgyRepository.findAll().stream()
                .map(PtawyStgy::getId)
                .collect(java.util.stream.Collectors.toSet());
        return stgyRvsnService.latestPerStrategy(StgyTyp.PTAWY).stream()
                .filter(r -> !alive.contains(r.getStgyId()))
                .map(r -> new PtawyStgyDeletedResponse(
                        r.getStgyId(), stgyRvsnService.snapshotName(r), r.getRvsnNo(), r.getCreatedAt()))
                .toList();
    }

    public PtawyStgy load(Long id) {
        return ptawyStgyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적치 전략입니다: " + id));
    }

    /**
     * 저장 검증 (P2): 전략명·단계 1개 이상 필수, mthd_cd 레지스트리 실존 + deprecated 금지,
     * 파라미터 스키마 검증, 조건 필드·연산자·값 개수 검증, 정렬 기준 검증.
     */
    public PtawyStgyDefinition validate(PtawyStgyDefinition definition) {
        if (definition.stgyNm() == null || definition.stgyNm().isBlank()) {
            throw new IllegalArgumentException("전략명은 필수입니다.");
        }
        if (definition.prty() != null && definition.prty() < 0) {
            throw new IllegalArgumentException("우선순위는 0 이상이어야 합니다.");
        }
        if (definition.stages() == null || definition.stages().isEmpty()) {
            throw new IllegalArgumentException("단계가 1개 이상 필요합니다.");
        }
        ConditionEvaluator.validate("적용대상", definition.tgtCond(), PutawayTargetField.BY_CODE);
        for (SortCriterion criterion : definition.locSrt() != null ? definition.locSrt() : List.<SortCriterion>of()) {
            if (!SORT_FIELDS.contains(criterion.field())) {
                throw new IllegalArgumentException("없는 정렬 기준입니다: " + criterion.field());
            }
            if (criterion.dir() != null && !"ASC".equalsIgnoreCase(criterion.dir())
                    && !"DESC".equalsIgnoreCase(criterion.dir())) {
                throw new IllegalArgumentException("정렬 방향은 ASC 또는 DESC여야 합니다: " + criterion.dir());
            }
        }

        List<PtawyStgyDefinition.StageDef> stages = new ArrayList<>();
        int seq = 0;
        for (PtawyStgyDefinition.StageDef stage : definition.stages()) {
            PutawayMethod method = registry.find(PutawayMethod.class, stage.mthdCd())
                    .orElseThrow(() -> new IllegalArgumentException("없는 적치 방식입니다: " + stage.mthdCd()));
            if (method.descriptor().deprecated()) {
                throw new IllegalArgumentException("은퇴한 방식은 새로 등록할 수 없습니다: " + method.descriptor().name());
            }
            Map<String, Object> para = ParamValidator.validate(
                    method.descriptor().name(), method.descriptor().params(), stage.mthdPara());
            ConditionEvaluator.validate(method.descriptor().name() + " 라인 조건", stage.lineCond(), PutawayTargetField.BY_CODE);
            ConditionEvaluator.validate(method.descriptor().name() + " 로케이션 조건", stage.locCond(), PutawayLocField.BY_CODE);
            stages.add(new PtawyStgyDefinition.StageDef(
                    stage.srtSeq() != null ? stage.srtSeq() : seq, stage.mthdCd(), para,
                    stage.lineCond() != null ? stage.lineCond() : List.of(),
                    stage.locCond() != null ? stage.locCond() : List.of()));
            seq++;
        }
        return new PtawyStgyDefinition(definition.stgyNm(),
                definition.prty() != null ? definition.prty() : 0,
                definition.untSpltYn() != null && definition.untSpltYn(),
                definition.tgtCond() != null ? definition.tgtCond() : List.of(),
                definition.locSrt() != null ? definition.locSrt() : List.of(),
                stages);
    }

    private List<PtawyStgyStg> toStages(PtawyStgyDefinition definition) {
        return definition.stages().stream()
                .map(s -> PtawyStgyStg.builder()
                        .srtSeq(s.srtSeq())
                        .mthdCd(s.mthdCd())
                        .mthdPara(s.mthdPara())
                        .lineCond(s.lineCond())
                        .locCond(s.locCond())
                        .build())
                .toList();
    }
}
