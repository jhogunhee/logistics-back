package com.project.wmsback.strategy.wave.dto;

import java.time.LocalDate;

/**
 * 웨이브 전략 실행 요청.
 * wavStgyId를 주면 그 전략만(선택실행), 비우면 전 전략을 우선순위 순으로(자동실행) 실행한다.
 * 출고예정일 범위는 대상 주문을 좁히는 선택 조건이며, 비우면 미편성 주문 전체가 대상이다.
 * 웨이브는 「같은 날 나갈 주문」을 묶는 단위라 기간의 기준이 주문일이 아니라 출고예정일이다.
 */
public record WaveStgyExecRequest(
        Long wavStgyId,
        LocalDate expctDeFrom,
        LocalDate expctDeTo
) {
}
