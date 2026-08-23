package com.project.wmsback.strategy.allocation.service;

import com.project.wmsback.strategy.allocation.component.AlocDstrb;
import com.project.wmsback.strategy.allocation.component.AlocRstrct;
import com.project.wmsback.strategy.allocation.dto.AlocStgyDefinition;
import com.project.wmsback.strategy.allocation.dto.AlocStgyResponse;
import com.project.wmsback.strategy.allocation.dto.AlocStgySummaryResponse;
import com.project.wmsback.strategy.allocation.entity.AlocStgy;
import com.project.wmsback.strategy.allocation.entity.AlocStgySlot;
import com.project.wmsback.strategy.allocation.entity.AlocSlotTyp;
import com.project.wmsback.strategy.allocation.field.AlocInvnField;
import com.project.wmsback.strategy.allocation.field.AlocLineField;
import com.project.wmsback.strategy.allocation.field.AlocTgtField;
import com.project.wmsback.strategy.allocation.field.AlocLineTarget;
import com.project.wmsback.strategy.allocation.field.InvnSortField;
import com.project.wmsback.strategy.allocation.field.OdrSortField;
import com.project.wmsback.strategy.allocation.repository.AlocStgyRepository;
import com.project.wmsback.strategy.core.condition.ConditionEvaluator;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.condition.SortCriterion;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.service.StgyRvsnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 할당 전략 관리 (CRUD·검증·리비전·선택). 저장 검증이 P2의 관문 —
 * 실행 불가하거나 의도가 성립하지 않는 정의는 저장되지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlocStgyService {

    private final AlocStgyRepository alocStgyRepository;
    private final StgyRvsnService stgyRvsnService;

    public List<AlocStgySummaryResponse> list() {
        return alocStgyRepository.findAllByOrderByPrtyAscIdAsc().stream()
                .map(AlocStgySummaryResponse::from)
                .toList();
    }

    public AlocStgyResponse get(Long id) {
        return AlocStgyResponse.from(load(id));
    }

    @Transactional
    public AlocStgyResponse create(AlocStgyDefinition definition) {
        AlocStgyDefinition normalized = validate(definition);
        AlocStgy stgy = AlocStgy.builder()
                .stgyNm(normalized.stgyNm())
                .prty(normalized.prty())
                .tgtCond(normalized.tgtCond())
                .build();
        normalized.slots().forEach(slot -> stgy.addSlot(toEntity(slot)));
        alocStgyRepository.save(stgy);
        stgyRvsnService.snapshot(StgyTyp.ALOC, stgy.getId(), stgy.getLastRvsnNo(), normalized);
        return AlocStgyResponse.from(stgy);
    }

    @Transactional
    public AlocStgyResponse update(Long id, AlocStgyDefinition definition) {
        AlocStgy stgy = load(id);
        AlocStgyDefinition normalized = validate(definition);
        List<AlocStgySlot> slots = normalized.slots().stream().map(AlocStgyService::toEntity).toList();
        long rvsnNo = stgy.applyDefinition(normalized.stgyNm(), normalized.prty(),
                normalized.tgtCond(), slots);
        stgyRvsnService.snapshot(StgyTyp.ALOC, stgy.getId(), rvsnNo, normalized);
        return AlocStgyResponse.from(stgy);
    }

    /**
     * 물리삭제 (D4 — 실행 제외 = 삭제). 리비전·실행 로그는 감사용으로 남는다.
     * 이 전략이 만든 할당은 건드리지 않는다 — {@code outb_alloc.aloc_stgy_id}는 느슨한 참조라
     * 원본이 사라져도 "무엇이 만들었는지"의 기록으로 남는다.
     */
    @Transactional
    public void delete(Long id) {
        alocStgyRepository.delete(load(id));
    }

    public AlocStgy load(Long id) {
        return alocStgyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 할당 전략입니다: " + id));
    }

    // ── 전략 선택 ────────────────────────────────────────────────────────────

    /**
     * 실행 1회에 쓸 전략을 고른다 — {@code prty} 순으로 순회하며 <b>대상 라인 전부</b>가
     * 적용대상 조건을 만족하는 첫 전략. 없으면 empty이고 호출부는 기본 동작으로 실행한다.
     *
     * <p>「전부 만족」인 이유: 전략이 실행 1회당 1건이라 「일부만 만족」에 줄 의미가 없다.
     * 조건이 빈 전략은 무조건 만족이므로 자연히 폴백이 된다 — 별도 분기가 필요 없다.
     */
    public Optional<AlocStgy> select(List<AlocLineTarget> targets) {
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        for (AlocStgy stgy : alocStgyRepository.findAllByOrderByPrtyAscIdAsc()) {
            boolean all = targets.stream().allMatch(target ->
                    ConditionEvaluator.matchesAll(stgy.getTgtCond(), AlocTgtField.BY_CODE, target));
            if (all) {
                return Optional.of(stgy);
            }
        }
        return Optional.empty();
    }

    // ── 저장 검증 (P2) ───────────────────────────────────────────────────────

    /**
     * 저장 검증. DB CHECK가 볼 수 없는 것 — <b>행 개수와 행 사이의 관계</b> — 이 여기 몫이다.
     *
     * <p>검증 실패는 전부 저장 거부다. 「등록은 되는데 실행하면 오류」도, 「저장은 되는데 의도가
     * 성립하지 않는」 정의도 만들지 않는다.
     */
    public AlocStgyDefinition validate(AlocStgyDefinition definition) {
        if (definition.stgyNm() == null || definition.stgyNm().isBlank()) {
            throw new IllegalArgumentException("전략명은 필수입니다.");
        }
        if (definition.prty() != null && definition.prty() < 0) {
            throw new IllegalArgumentException("우선순위는 0 이상이어야 합니다.");
        }
        List<FieldCondition> tgtCond = definition.tgtCond() != null
                ? List.copyOf(definition.tgtCond()) : List.of();
        ConditionEvaluator.validate("적용대상", tgtCond, AlocTgtField.BY_CODE);

        // 타입 없는 슬롯은 slotsOf에서 조용히 빠져 "저장했는데 사라진" 슬롯이 된다 — 먼저 막는다
        if (definition.slots() != null
                && definition.slots().stream().anyMatch(slot -> slot.slotTyp() == null)) {
            throw new IllegalArgumentException("슬롯 타입이 없는 슬롯이 있습니다.");
        }

        List<AlocStgyDefinition.SlotDef> slots = new ArrayList<>();
        for (AlocSlotTyp slotTyp : AlocSlotTyp.values()) {
            slots.addAll(validateSlots(slotTyp, definition.slotsOf(slotTyp)));
        }
        return new AlocStgyDefinition(definition.stgyNm(),
                definition.prty() != null ? definition.prty() : 0, tgtCond, slots);
    }

    /** 슬롯 타입 하나의 목록 검증 + srt_seq 정규화(1..n). 화면 순서와 저장 순서를 일치시킨다 */
    private List<AlocStgyDefinition.SlotDef> validateSlots(AlocSlotTyp slotTyp,
                                                           List<AlocStgyDefinition.SlotDef> slots) {
        if (slots.isEmpty()) {
            return List.of();
        }
        if (!slotTyp.isMulti() && slots.size() > 1) {
            throw new IllegalArgumentException(
                    slotTyp.getLabel() + "은(는) 1건만 등록할 수 있습니다 (등록 " + slots.size() + "건).");
        }

        List<AlocStgyDefinition.SlotDef> normalized = new ArrayList<>();
        Set<String> seenCmpnt = new HashSet<>();
        int seq = 1;
        for (AlocStgyDefinition.SlotDef slot : slots) {
            String label = slotTyp.getLabel() + " " + seq + "번";
            String cmpntCd = validateCmpnt(slotTyp, slot, label, seenCmpnt);
            validatePara(slotTyp, slot, label);
            List<FieldCondition> cond = validateCond(slotTyp, slot, label);
            normalized.add(new AlocStgyDefinition.SlotDef(slotTyp, seq++, cmpntCd,
                    slot.paraOrEmpty(), cond));
        }
        if (slotTyp == AlocSlotTyp.DSTRB) {
            validateLastDstrbOpen(normalized);
        }
        if (slotTyp == AlocSlotTyp.INVN_FLTR) {
            validateTiersEndOpen(normalized);
        }
        return normalized;
    }

    private String validateCmpnt(AlocSlotTyp slotTyp, AlocStgyDefinition.SlotDef slot,
                                 String label, Set<String> seenCmpnt) {
        if (!slotTyp.isHasCmpnt()) {
            // 재고위치 슬롯에 구현체가 오면 화면/클라이언트가 잘못 보낸 것이다.
            if (slotTyp == AlocSlotTyp.INVN_FLTR && slot.cmpntCd() != null) {
                throw new IllegalArgumentException(label + ": 재고위치 슬롯은 구현체를 갖지 않습니다.");
            }
            // 정렬 슬롯에 남아 있는 옛 구현체 코드는 거부하지 않고 버린다 — 정렬은 기준 목록이
            // 정의 전부라 그 값이 뜻하는 것이 없고, 옛 화면이 보낸 값 하나로 저장이 막히면 안 된다.
            return null;
        }
        String cmpntCd = slot.cmpntCd();
        if (cmpntCd == null || cmpntCd.isBlank()) {
            throw new IllegalArgumentException(label + ": 구현체를 선택하세요.");
        }
        boolean exists = switch (slotTyp) {
            case RSTRCT -> AlocRstrct.find(cmpntCd).isPresent();
            case DSTRB -> AlocDstrb.find(cmpntCd).isPresent();
            case INVN_FLTR, INVN_SRT, ODR_SRT -> false;
        };
        if (!exists) {
            throw new IllegalArgumentException(label + ": 없는 구현체입니다 — " + cmpntCd);
        }
        // 제약은 조건 없이 전 후보에 AND로 걸리므로 같은 구현체를 두 번 등록하면 뒤엣것이
        // 아무 일도 하지 않는다. 분배는 다르다 — 같은 방식이라도 cond가 다르면 대상이 다르므로
        // 「중요 점포 먼저 순차, 나머지 순차」가 정상적인 정의다.
        if (slotTyp == AlocSlotTyp.RSTRCT && !seenCmpnt.add(cmpntCd)) {
            throw new IllegalArgumentException(
                    slotTyp.getLabel() + ": 같은 제약을 두 번 등록했습니다 — " + cmpntCd);
        }
        return cmpntCd;
    }

    private void validatePara(AlocSlotTyp slotTyp, AlocStgyDefinition.SlotDef slot, String label) {
        switch (slotTyp) {
            case RSTRCT -> AlocRstrct.of(slot.cmpntCd()).validatePara(slot.paraOrEmpty());
            case INVN_SRT -> validateCriteria(label, slot, code -> InvnSortField.find(code).isPresent());
            case ODR_SRT -> validateCriteria(label, slot, code -> OdrSortField.find(code).isPresent());
            case INVN_FLTR, DSTRB -> {
                if (!slot.paraOrEmpty().isEmpty()) {
                    throw new IllegalArgumentException(label + ": 이 슬롯은 파라미터를 쓰지 않습니다.");
                }
            }
        }
    }

    /** 정렬 기준 검증 — 실행이 쓰는 파싱({@link AlocStgyDefinition.SlotDef#criteria})을 그대로 쓴다 */
    private void validateCriteria(String label, AlocStgyDefinition.SlotDef slot,
                                  java.util.function.Predicate<String> fieldExists) {
        List<SortCriterion> criteria = slot.criteria();
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException(label + ": 정렬 기준이 1개 이상 필요합니다.");
        }
        Set<String> seen = new HashSet<>();
        for (SortCriterion criterion : criteria) {
            if (criterion.field() == null || !fieldExists.test(criterion.field())) {
                throw new IllegalArgumentException(label + ": 없는 정렬 기준입니다 — " + criterion.field());
            }
            if (!"ASC".equalsIgnoreCase(criterion.dir()) && !"DESC".equalsIgnoreCase(criterion.dir())) {
                throw new IllegalArgumentException(label + ": 정렬 방향은 ASC 또는 DESC여야 합니다 — "
                        + criterion.dir());
            }
            // 같은 기준을 두 번 넣으면 뒤엣것은 비교에 도달하지 못한다 (앞에서 이미 순서가 정해짐)
            if (!seen.add(criterion.field())) {
                throw new IllegalArgumentException(label + ": 같은 정렬 기준이 중복됐습니다 — "
                        + criterion.field());
            }
        }
    }

    private List<FieldCondition> validateCond(AlocSlotTyp slotTyp, AlocStgyDefinition.SlotDef slot,
                                              String label) {
        List<FieldCondition> cond = slot.condOrEmpty();
        switch (slotTyp) {
            case INVN_FLTR -> ConditionEvaluator.validate(label, cond, AlocInvnField.BY_CODE);
            case DSTRB -> ConditionEvaluator.validate(label, cond, AlocLineField.BY_CODE);
            case RSTRCT, INVN_SRT, ODR_SRT -> {
                if (!cond.isEmpty()) {
                    throw new IllegalArgumentException(label + ": 이 슬롯은 조건을 쓰지 않습니다.");
                }
            }
        }
        return List.copyOf(cond);
    }

    /**
     * 마지막 분배 슬롯은 조건이 비어 있어야 한다. 조건 있는 슬롯으로 끝나면 <b>어느 조건에도
     * 걸리지 않은 라인</b>이 재고를 눈앞에 두고 0을 받는데, 그건 정의의 의도가 아니라 누락이다.
     */
    private void validateLastDstrbOpen(List<AlocStgyDefinition.SlotDef> slots) {
        AlocStgyDefinition.SlotDef last = slots.get(slots.size() - 1);
        if (!last.condOrEmpty().isEmpty()) {
            throw new IllegalArgumentException(
                    "마지막 분배는 조건 없이 전 라인을 대상으로 해야 합니다 — "
                            + "조건에 걸리지 않은 라인이 재고를 두고도 받지 못합니다.");
        }
    }

    /**
     * 계층은 조건 없는 계층으로 끝나야 하고, 조건 없는 계층은 거기에만 올 수 있다.
     * 앞에 두면 후보 전체를 가져가 뒤 계층이 죽고, 끝에 없으면 어느 계층에도 맞지 않는 재고를
     * 자동할당이 영영 쓰지 않는다 — 보충 이동이 없는 이 창고에서 그 물량은 곧 결품이다.
     * 마지막 분배가 조건 없이 끝나야 하는 것과 같은 규칙이다.
     */
    private void validateTiersEndOpen(List<AlocStgyDefinition.SlotDef> slots) {
        for (int i = 0; i < slots.size() - 1; i++) {
            if (slots.get(i).condOrEmpty().isEmpty()) {
                throw new IllegalArgumentException((i + 1) + "번 재고위치 계층에 조건이 없습니다 — "
                        + "조건 없는 계층은 후보 전체를 가져가므로 마지막에만 둘 수 있습니다.");
            }
        }
        if (!slots.get(slots.size() - 1).condOrEmpty().isEmpty()) {
            throw new IllegalArgumentException(
                    "마지막 재고위치 계층은 조건 없이 남은 후보 전체를 받아야 합니다 — "
                            + "어느 계층에도 맞지 않는 재고는 자동할당이 쓰지 않아 그만큼 결품이 됩니다.");
        }
    }

    private static AlocStgySlot toEntity(AlocStgyDefinition.SlotDef slot) {
        return AlocStgySlot.builder()
                .slotTyp(slot.slotTyp())
                .srtSeq(slot.srtSeq())
                .cmpntCd(slot.cmpntCd())
                .para(slot.paraOrEmpty())
                .cond(slot.condOrEmpty())
                .build();
    }
}
