package com.project.wmsback.strategy.core.condition;

import java.util.Set;

/**
 * 조건을 걸 수 있는 필드의 규약. 도메인별 enum이 구현한다 — 필드 추가 = enum 상수 추가로
 * 끝나고 화면에 자동 노출된다. 「조건기준 → 비교할 컬럼」의 매핑을 데이터가 아니라 코드가
 * 소유하므로, 매핑이 비어 등록만 되고 동작하지 않는 옵션이 구조적으로 생길 수 없다 (P1).
 */
public interface ConditionField<T> {

    /** 저장에 쓰이는 code (enum name) */
    String code();

    String label();

    Set<ConditionOperator> allowedOps();

    /** 값 선택지 소스 (GET /strategy/meta/options/{source}). 직접입력이면 null */
    String optionSource();

    /** 판정 대상에서 실제값 추출. null = 속성 없음 (부정 연산자만 참) */
    String extract(T target);
}
