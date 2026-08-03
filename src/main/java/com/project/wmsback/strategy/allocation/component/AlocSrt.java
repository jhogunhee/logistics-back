package com.project.wmsback.strategy.allocation.component;

import com.project.wmsback.strategy.core.condition.SortCriterion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 정렬 슬롯(INVN_SRT·ODR_SRT)의 구현체. 하나뿐이고 다중 기준은 파라미터가 갖는다 —
 * 「유통기한 ASC 다음 피킹순위 ASC」 같은 정의를 구현체 종류로 표현하면 조합 수만큼 상수가
 * 필요해지지만, {@code para.criteria} 목록으로 표현하면 순서 편집만으로 끝난다.
 *
 * <p>선택지가 하나뿐이라 화면은 이 피커를 감추고 기준 목록만 보여준다 —
 * 값이 하나인 드롭다운은 정보가 아니라 잡음이다.
 */
public enum AlocSrt {

    MULTI_SORT("다중 정렬", "정렬 기준을 순서대로 나열합니다. 앞 기준이 같을 때 다음 기준으로 비교합니다.");

    /** para 키 — 값은 {@code [{"field":"…","dir":"ASC|DESC"},…]} */
    public static final String PARA_CRITERIA = "criteria";

    private final String label;
    private final String dscr;

    AlocSrt(String label, String dscr) {
        this.label = label;
        this.dscr = dscr;
    }

    public String label() {
        return label;
    }

    public String dscr() {
        return dscr;
    }

    public boolean deprecated() {
        return false;
    }

    /**
     * para에서 정렬 기준 목록을 꺼낸다. 저장 검증과 실행이 <b>같은 파싱</b>을 쓴다 —
     * 두 곳이 다르게 읽으면 "저장은 됐는데 실행은 다르게 도는" 정의가 생긴다.
     * 형식이 어긋난 원소는 여기서 걸러지지 않고 빈 field로 넘어가 검증에서 사유와 함께 거부된다.
     */
    public static List<SortCriterion> criteriaOf(Map<String, Object> para) {
        Object raw = para != null ? para.get(PARA_CRITERIA) : null;
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

    public static Optional<AlocSrt> find(String code) {
        for (AlocSrt srt : values()) {
            if (srt.name().equals(code)) {
                return Optional.of(srt);
            }
        }
        return Optional.empty();
    }
}
