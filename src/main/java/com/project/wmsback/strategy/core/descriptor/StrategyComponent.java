package com.project.wmsback.strategy.core.descriptor;

/**
 * 전략 구성요소 공통 규약. 구현체는 Spring Bean으로 등록되고
 * StrategyComponentRegistry가 "구성요소 종류 인터페이스"별로 인덱싱한다.
 */
public interface StrategyComponent {

    ComponentDescriptor descriptor();
}
