package com.project.wmsback.strategy.wave.dto;

import java.util.List;

/**
 * 미리보기 결과. DB 변경 없음 — 실행과 같은 판정 함수를 쓰되 편입하지 않는다.
 * 편입 건수만이 아니라 대상 건수와 주문별 사유를 함께 돌려준다 — 무엇이 왜 빠졌는지 보이지 않으면
 * 이름과 조건이 어긋난 전략이 그대로 운영에 나간다.
 */
public record WavPreviewResponse(
        int tgtCount,
        int matchedCount,
        List<WaveMatchResult> orders
) {
}
