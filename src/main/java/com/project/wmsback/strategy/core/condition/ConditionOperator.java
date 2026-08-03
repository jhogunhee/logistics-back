package com.project.wmsback.strategy.core.condition;

import java.util.List;

/**
 * 조건 비교 연산자. 판정은 항상 test(실제값, 조건값들) — 인자 순서가 타입으로 고정돼
 * 「실제값과 조건값을 거꾸로 넘겨 비교가 뒤집히는」 버그 계열이 구조적으로 생길 수 없다.
 * GE/LE/BETWEEN은 문자열 사전순 비교다 — ISO 일자(yyyy-MM-dd)처럼 사전순 = 값순인 필드에만
 * 허용 연산자로 선언할 것.
 */
public enum ConditionOperator {
    EQ, NE, IN, NOT_IN, GE, LE, BETWEEN, LIKE;

    /** 연산자별 조건값 개수 (min, max). 저장 시 검증에 쓴다 */
    public int minVals() {
        return this == BETWEEN ? 2 : 1;
    }

    public int maxVals() {
        return switch (this) {
            case IN, NOT_IN -> Integer.MAX_VALUE;
            case BETWEEN -> 2;
            default -> 1;
        };
    }

    /** actual이 null(속성 없음)이면 부정 연산자(NE, NOT_IN)만 참 */
    public boolean test(String actual, List<String> vals) {
        if (actual == null) {
            return this == NE || this == NOT_IN;
        }
        return switch (this) {
            case EQ -> actual.equals(vals.get(0));
            case NE -> !actual.equals(vals.get(0));
            case IN -> vals.contains(actual);
            case NOT_IN -> !vals.contains(actual);
            case GE -> actual.compareTo(vals.get(0)) >= 0;
            case LE -> actual.compareTo(vals.get(0)) <= 0;
            case BETWEEN -> actual.compareTo(vals.get(0)) >= 0 && actual.compareTo(vals.get(1)) <= 0;
            case LIKE -> actual.contains(vals.get(0));
        };
    }
}
