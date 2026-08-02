package com.project.wmsback.strategy.core.condition;

/** 정렬 기준 1건. dir = ASC | DESC. JSONB 배열(loc_srt)의 원소 — 앞 기준부터 적용 */
public record SortCriterion(String field, String dir) {

    public boolean asc() {
        return !"DESC".equalsIgnoreCase(dir);
    }
}
