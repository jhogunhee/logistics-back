package com.project.wmsback.strategy.wave.dto;

import java.time.LocalDate;

/**
 * 웨이브 전략 실행 요청.
 * wavStgyId를 주면 그 전략만(선택실행), 비우면 전 전략을 우선순위 순으로(자동실행) 실행한다.
 * 대상 출고예정일은 <b>하루 단위 필수</b>다 — 웨이브는 「같은 날 나갈 주문」을 묶는 단위라
 * (출고예정일이 다른 주문은 한 웨이브에 담지 않는다) 실행 범위가 기간이면 전략 하나가
 * 날짜별로 웨이브를 쪼개야 하는데, 「전략마다 웨이브 하나」 규칙을 지키려면 범위가 하루여야 한다.
 * 기준이 주문일이 아니라 출고예정일인 것은 그대로다.
 */
public record WaveStgyExecRequest(
        Long wavStgyId,
        LocalDate expctDe
) {
}
