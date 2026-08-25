package com.project.wmsback.strategy.inspection.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 검수 입력 전 힌트 — 상품·입고일자마다 「입고 가능한 가장 이른 제조일자」.
 * minMfgDt는 규칙별 하한 중 가장 늦은 날(모든 규칙을 동시에 만족해야 하므로), 하한이 하나도 없으면 null.
 * rules는 화면 툴팁용 — 어느 규칙이 어디까지 허용하는지. 정책이 없으면 비어 있다.
 */
public record InspMinMfgDtResponse(List<Item> items) {

    public record Item(Long prodId, LocalDate receiptDt, LocalDate minMfgDt, List<RuleMin> rules) {
    }

    /** minMfgDt null = 이 규칙은 이 상품에 하한을 내지 않는다(미관리·기준 재고 없음) */
    public record RuleMin(String ruleCd, String ruleName, LocalDate minMfgDt) {
    }
}
