package com.project.wmsback.strategy.inspection.rule;

import com.project.wmsback.strategy.core.descriptor.StrategyComponent;
import com.project.wmsback.strategy.core.param.ParamValues;

import java.util.Optional;

/**
 * 검수 규칙 구현체 규약. 구현체를 @Component로 추가하면 화면(메타 API)·저장 검증·실행에
 * 자동으로 나타난다 — 새 제약 추가는 이 인터페이스 구현이 전부다.
 */
public interface InspectionRule extends StrategyComponent {

    /**
     * 이 라인이 규칙의 관리 대상이 아니면 사유 반환 (예: 유통기한 미관리 상품).
     * 스킵은 통과로 간주하되 trace에 사유가 남는다 (레거시의 암묵 스킵을 가시화).
     */
    Optional<String> skipReason(InspectionContext ctx);

    /** 위반 없으면 empty, 위반 시 사유 반환. 예외를 흐름 제어에 쓰지 않는다 */
    Optional<Violation> check(InspectionContext ctx, ParamValues params);
}
