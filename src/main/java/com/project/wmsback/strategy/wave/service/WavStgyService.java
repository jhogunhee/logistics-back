package com.project.wmsback.strategy.wave.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.wmsback.strategy.core.condition.ConditionEvaluator;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.dto.RvsnResponse;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.service.StgyRvsnService;
import com.project.wmsback.strategy.wave.dto.WavStgyDefinition;
import com.project.wmsback.strategy.wave.dto.WavStgyResponse;
import com.project.wmsback.strategy.wave.dto.WavStgySummaryResponse;
import com.project.wmsback.strategy.wave.entity.WavStgy;
import com.project.wmsback.strategy.wave.field.WaveOrderField;
import com.project.wmsback.strategy.wave.repository.WavStgyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 웨이브 전략 관리 (CRUD·리비전). 저장 검증이 P2의 관문 — 실행 불가 정의는 저장되지 않는다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WavStgyService {

    private final WavStgyRepository wavStgyRepository;
    private final StgyRvsnService stgyRvsnService;

    public List<WavStgySummaryResponse> list() {
        // 실행 순서 그대로 보여준다 — 화면 정렬이 곧 편성 선점 순서다
        return wavStgyRepository.findAllByOrderByPrtyAscIdAsc().stream()
                .map(WavStgySummaryResponse::from)
                .toList();
    }

    public WavStgyResponse get(Long id) {
        return WavStgyResponse.from(load(id));
    }

    @Transactional
    public WavStgyResponse create(WavStgyDefinition definition) {
        WavStgyDefinition normalized = validate(definition);
        WavStgy stgy = WavStgy.builder()
                .stgyNm(normalized.stgyNm())
                .prty(normalized.prty())
                .condGrp(normalized.condGrp())
                .build();
        wavStgyRepository.save(stgy);
        stgyRvsnService.snapshot(StgyTyp.WAV, stgy.getId(), stgy.getLastRvsnNo(), normalized);
        return WavStgyResponse.from(stgy);
    }

    @Transactional
    public WavStgyResponse update(Long id, WavStgyDefinition definition) {
        WavStgy stgy = load(id);
        WavStgyDefinition normalized = validate(definition);
        long rvsnNo = stgy.applyDefinition(normalized.stgyNm(), normalized.prty(), normalized.condGrp());
        stgyRvsnService.snapshot(StgyTyp.WAV, stgy.getId(), rvsnNo, normalized);
        return WavStgyResponse.from(stgy);
    }

    /**
     * 물리삭제 (D4 — 실행 제외 = 삭제). 리비전·실행 로그는 감사용으로 남는다.
     * 이 전략이 만든 웨이브는 건드리지 않는다 — outb_wave.wav_stgy_id는 느슨한 참조라
     * 원본이 사라져도 "무엇이 만들었는지"의 기록으로 남는다.
     */
    @Transactional
    public void delete(Long id) {
        wavStgyRepository.delete(load(id));
    }

    public List<RvsnResponse> revisions(Long id) {
        return stgyRvsnService.list(StgyTyp.WAV, id);
    }

    public JsonNode revision(Long id, Long rvsnNo) {
        return stgyRvsnService.snapshotTree(StgyTyp.WAV, id, rvsnNo);
    }

    public WavStgy load(Long id) {
        return wavStgyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브 전략입니다: " + id));
    }

    /**
     * 저장 검증 (P2): 전략명 필수, 우선순위 음수 금지, 조건그룹 1개 이상,
     * <b>빈 그룹 금지</b>, 조건의 필드·연산자·값 개수 검증.
     *
     * <p>빈 그룹을 막는 이유: 조건 0건은 "무조건 참"이라 그 그룹 하나로 미편성 주문 전부를 쓸어담는다.
     * DB CHECK는 배열 길이만 보므로 {@code [[]]}가 통과한다 — 여기가 유일한 방어선이다.
     */
    public WavStgyDefinition validate(WavStgyDefinition definition) {
        if (definition.stgyNm() == null || definition.stgyNm().isBlank()) {
            throw new IllegalArgumentException("전략명은 필수입니다.");
        }
        if (definition.prty() != null && definition.prty() < 0) {
            throw new IllegalArgumentException("우선순위는 0 이상이어야 합니다.");
        }
        if (definition.condGrp() == null || definition.condGrp().isEmpty()) {
            throw new IllegalArgumentException("조건그룹이 1개 이상 필요합니다 — 조건 없는 전략은 미편성 주문 전부를 편입합니다.");
        }

        List<List<FieldCondition>> groups = new ArrayList<>();
        int idx = 1;
        for (List<FieldCondition> group : definition.condGrp()) {
            if (group == null || group.isEmpty()) {
                throw new IllegalArgumentException(idx + "번 조건그룹이 비어 있습니다 — 조건이 없으면 모든 주문이 편입됩니다.");
            }
            ConditionEvaluator.validate(idx + "번 조건그룹", group, WaveOrderField.BY_CODE);
            groups.add(List.copyOf(group));
            idx++;
        }
        return new WavStgyDefinition(definition.stgyNm(),
                definition.prty() != null ? definition.prty() : 0, groups);
    }
}
