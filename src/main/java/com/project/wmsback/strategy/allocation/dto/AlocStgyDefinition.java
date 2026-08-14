package com.project.wmsback.strategy.allocation.dto;

import com.project.wmsback.strategy.allocation.entity.AlocSlotTyp;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.condition.SortCriterion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 할당 전략 정의 — 저장 요청 본문이자 리비전 스냅샷의 형태이자 <b>산정기의 입력</b>이다.
 * 셋이 같은 타입인 덕분에 미저장 정의로도 실전과 똑같이 미리보기할 수 있다 (P4).
 *
 * <p>{@code tgtCond}가 비면 전체 매칭 폴백 전략이다. {@code slots}가 비면 전 슬롯이
 * 기본 동작(FEFO · 점포 잔여수명 · 순차 소진)으로 실행된다 — 필수 슬롯이 없다.
 */
public record AlocStgyDefinition(
        String stgyNm,
        Integer prty,
        List<FieldCondition> tgtCond,
        List<SlotDef> slots
) {

    /**
     * 슬롯 1개. {@code cmpntCd}는 구현체 축이 있는 슬롯(RSTRCT·DSTRB)만 채우고,
     * {@code cond}는 INVN_FLTR(계층 지정)·DSTRB(대상 선별)만,
     * {@code para}는 RSTRCT(구현체 파라미터)·정렬 슬롯(criteria)만 쓴다.
     */
    public record SlotDef(
            AlocSlotTyp slotTyp,
            Integer srtSeq,
            String cmpntCd,
            Map<String, Object> para,
            List<FieldCondition> cond
    ) {

        /** 정렬 슬롯 para의 기준 목록 키 — 값은 {@code [{"field":"…","dir":"ASC|DESC"},…]} */
        public static final String PARA_CRITERIA = "criteria";

        public Map<String, Object> paraOrEmpty() {
            return para != null ? para : Map.of();
        }

        /**
         * 정렬 기준 목록. 저장 검증과 실행이 <b>같은 파싱</b>을 쓴다 — 두 곳이 다르게 읽으면
         * "저장은 됐는데 실행은 다르게 도는" 정의가 생긴다. 형식이 어긋난 원소는 여기서 걸러지지
         * 않고 빈 field로 넘어가 검증에서 사유와 함께 거부된다.
         */
        public List<SortCriterion> criteria() {
            Object raw = paraOrEmpty().get(PARA_CRITERIA);
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<SortCriterion> criteria = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object field = map.get("field");
                    Object dir = map.get("dir");
                    criteria.add(new SortCriterion(
                            field != null ? field.toString() : null,
                            dir != null ? dir.toString() : "ASC"));
                }
            }
            return criteria;
        }

        public List<FieldCondition> condOrEmpty() {
            return cond != null ? cond : List.of();
        }

        public int seq() {
            return srtSeq != null ? srtSeq : 0;
        }
    }

    /** 슬롯 타입별 목록 (srt_seq 순). 없으면 빈 목록 = 그 역할은 기본 동작 */
    public List<SlotDef> slotsOf(AlocSlotTyp slotTyp) {
        if (slots == null) {
            return List.of();
        }
        return slots.stream()
                .filter(slot -> slot.slotTyp() == slotTyp)
                .sorted(Comparator.comparingInt(SlotDef::seq))
                .toList();
    }

    /** 단일 슬롯 조회 — 저장 검증이 2건 이상을 거부하므로 실행 시점엔 0 또는 1건이다 */
    public SlotDef singleSlot(AlocSlotTyp slotTyp) {
        List<SlotDef> found = slotsOf(slotTyp);
        return found.isEmpty() ? null : found.get(0);
    }
}
