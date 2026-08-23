package com.project.wmsback.strategy.wave.dto;

import java.util.List;

/**
 * 웨이브 편성의 판정 근거 — 미리보기 응답과 실행 로그 dcsn_trc가 같은 레코드를 사용한다
 *
 * <p>편입 건수만이 아니라 대상 건수와 주문별 사유를 함께 담는다 — 무엇이 왜 빠졌는지 보이지
 * 않으면 이름과 조건이 어긋난 전략이 그대로 운영에 나간다. 미리보기는 이 값을 돌려주기만 하고
 * 편입하지 않는다 (P4 — 판정 함수는 실행과 같다).
 */
public record WaveDecisionTrace(
        int tgtCount,
        int matchedCount,
        List<WaveMatchResult> orders
) {
}
