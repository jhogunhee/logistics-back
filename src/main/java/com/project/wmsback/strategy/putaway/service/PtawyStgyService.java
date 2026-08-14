package com.project.wmsback.strategy.putaway.service;

import com.project.wmsback.strategy.core.condition.ConditionEvaluator;
import com.project.wmsback.strategy.core.condition.ConditionOperator;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.condition.SortCriterion;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.service.StgyRvsnService;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyDefinition;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyResponse;
import com.project.wmsback.strategy.putaway.dto.PtawyStgySummaryResponse;
import com.project.wmsback.strategy.putaway.entity.PtawyStgy;
import com.project.wmsback.strategy.putaway.entity.PtawyStgyStg;
import com.project.wmsback.strategy.putaway.field.PutawayLocField;
import com.project.wmsback.strategy.putaway.field.PutawaySortField;
import com.project.wmsback.strategy.putaway.field.PutawayTargetField;
import com.project.wmsback.strategy.putaway.component.PutawayMethod;
import com.project.wmsback.strategy.putaway.repository.PtawyStgyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 적치 전략 관리 (CRUD·리비전). 저장 검증이 P2의 관문 — 실행 불가 정의는 저장되지 않는다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PtawyStgyService {

    /** 적용대상 선택지 — 반품(RTNGS)은 스코프 아웃이라 제외. 재도입 시 여기와 옵션 소스에 추가 */
    private static final Set<String> ODR_DVSNS = Set.of("NRML", "URGT");

    private final PtawyStgyRepository ptawyStgyRepository;
    private final StgyRvsnService stgyRvsnService;

    public List<PtawyStgySummaryResponse> list() {
        // 유형 코드순, 전체(null)는 마지막 (PostgreSQL ASC = NULLS LAST) — 선택 규칙(유형 → 전체)과 무관한 표시 순서일 뿐
        return ptawyStgyRepository.findAll(Sort.by("odrDvsn", "id")).stream()
                .map(PtawyStgySummaryResponse::from)
                .toList();
    }

    public PtawyStgyResponse get(Long id) {
        return PtawyStgyResponse.from(load(id));
    }

    @Transactional
    public PtawyStgyResponse create(PtawyStgyDefinition definition) {
        PtawyStgyDefinition normalized = validate(definition);
        requireVacant(normalized.odrDvsn(), null);
        PtawyStgy stgy = PtawyStgy.builder()
                .stgyNm(normalized.stgyNm())
                .odrDvsn(normalized.odrDvsn())
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
        requireVacant(normalized.odrDvsn(), id);
        long rvsnNo = stgy.applyDefinition(normalized.stgyNm(), normalized.odrDvsn(),
                normalized.untSpltYn(), normalized.locSrt(), toStages(normalized));
        stgyRvsnService.snapshot(StgyTyp.PTAWY, stgy.getId(), rvsnNo, normalized);
        return PtawyStgyResponse.from(stgy);
    }

    /** 물리삭제. 리비전·실행 로그는 감사용으로 남는다 (조회 전용 — 복원 흐름 없음) */
    @Transactional
    public void delete(Long id) {
        ptawyStgyRepository.delete(load(id));
    }

    public PtawyStgy load(Long id) {
        return ptawyStgyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적치 전략입니다: " + id));
    }

    /** 유형당 1개 강제 — DB UNIQUE 인덱스의 친절한 선검사 (self = 수정 중인 자기 자신은 허용) */
    private void requireVacant(String odrDvsn, Long selfId) {
        Optional<PtawyStgy> occupied = odrDvsn == null
                ? ptawyStgyRepository.findByOdrDvsnIsNull()
                : ptawyStgyRepository.findByOdrDvsn(odrDvsn);
        occupied.filter(s -> !s.getId().equals(selfId)).ifPresent(s -> {
            throw new IllegalArgumentException("이 적용대상의 전략이 이미 있습니다: " + s.getStgyNm()
                    + " — 유형당 전략은 1개입니다. 기존 전략을 수정하세요.");
        });
    }

    /**
     * 저장 검증 (P2): 전략명·단계 1개 이상 필수, 적용대상은 전체(null)/정상/긴급만,
     * mthd_cd 실존(PutawayMethod enum) + deprecated 금지, 단계 조건 검증,
     * 적치위치는 "업무유형 IN 최대 1건" 지정 형태만, 정렬 기준 검증,
     * srt_seq는 받은 순서대로 1..n 재부여 (할당 슬롯과 같은 규칙 —
     * 클라이언트가 보낸 값을 그대로 믿으면 중복·구멍이 실행 순서가 된다).
     */
    public PtawyStgyDefinition validate(PtawyStgyDefinition definition) {
        if (definition.stgyNm() == null || definition.stgyNm().isBlank()) {
            throw new IllegalArgumentException("전략명은 필수입니다.");
        }
        if (definition.odrDvsn() != null && !ODR_DVSNS.contains(definition.odrDvsn())) {
            throw new IllegalArgumentException("없는 적용대상입니다: " + definition.odrDvsn());
        }
        if (definition.stages() == null || definition.stages().isEmpty()) {
            throw new IllegalArgumentException("단계가 1개 이상 필요합니다.");
        }
        for (SortCriterion criterion : definition.locSrt() != null ? definition.locSrt() : List.<SortCriterion>of()) {
            if (PutawaySortField.find(criterion.field()).isEmpty()) {
                throw new IllegalArgumentException("없는 정렬 기준입니다: " + criterion.field());
            }
            if (criterion.dir() != null && !"ASC".equalsIgnoreCase(criterion.dir())
                    && !"DESC".equalsIgnoreCase(criterion.dir())) {
                throw new IllegalArgumentException("정렬 방향은 ASC 또는 DESC여야 합니다: " + criterion.dir());
            }
        }

        List<PtawyStgyDefinition.StageDef> stages = new ArrayList<>();
        int seq = 1;
        for (PtawyStgyDefinition.StageDef stage : definition.stages()) {
            PutawayMethod method = PutawayMethod.find(stage.mthdCd())
                    .orElseThrow(() -> new IllegalArgumentException("없는 적치 방식입니다: " + stage.mthdCd()));
            if (method.deprecated()) {
                throw new IllegalArgumentException("은퇴한 방식은 새로 등록할 수 없습니다: " + method.label());
            }
            // 현재 적치 방식은 파라미터가 없다 — 방식에 파라미터가 생기면 여기서 방식별 검증을 추가한다
            if (stage.mthdPara() != null && !stage.mthdPara().isEmpty()) {
                throw new IllegalArgumentException(method.label() + ": 정의되지 않은 파라미터입니다 — " + stage.mthdPara().keySet());
            }
            ConditionEvaluator.validate(method.label() + " 조건", stage.lineCond(), PutawayTargetField.BY_CODE);
            validateLocAssign(method.label(), stage.locCond());
            stages.add(new PtawyStgyDefinition.StageDef(
                    seq++, stage.mthdCd(), Map.of(),
                    stage.lineCond() != null ? stage.lineCond() : List.of(),
                    stage.locCond() != null ? stage.locCond() : List.of()));
        }
        return new PtawyStgyDefinition(definition.stgyNm(), definition.odrDvsn(),
                definition.untSpltYn() != null && definition.untSpltYn(),
                definition.locSrt() != null ? definition.locSrt() : List.of(),
                stages);
    }

    /** 적치위치는 조건이 아니라 지정 — "존 업무유형 IN [값들]" 최대 1건만 허용한다 */
    private void validateLocAssign(String label, List<FieldCondition> locCond) {
        if (locCond == null || locCond.isEmpty()) {
            return;
        }
        if (locCond.size() > 1) {
            throw new IllegalArgumentException(label + ": 적치위치 지정은 1건만 가능합니다 (업무유형 값을 여러 개 선택하세요).");
        }
        FieldCondition assign = locCond.get(0);
        if (!PutawayLocField.BIZ_DVSN.name().equals(assign.fld()) || assign.op() != ConditionOperator.IN) {
            throw new IllegalArgumentException(label + ": 적치위치는 존 업무유형 지정만 가능합니다.");
        }
        ConditionEvaluator.validate(label + " 적치위치", locCond, PutawayLocField.BY_CODE);
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
