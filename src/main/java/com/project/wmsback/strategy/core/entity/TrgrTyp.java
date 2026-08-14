package com.project.wmsback.strategy.core.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전략 실행 트리거. AUTO는 2차 웨이브 자동실행용 선반영.
 * PREVIEW는 「결과를 반영하지 않는 산정」이라 실행 이력과 섞이면 안 되는 기록에 쓴다 —
 * 지금은 적치 일괄 추천이 유일하다(지시 생성 경로가 산정을 다시 돌리지 않아 근거가 거기밖에 없다).
 * 나머지 미리보기는 기록 자체를 하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum TrgrTyp {
    MANUAL("화면 조작"),
    AUTO("자동 실행"),
    PREVIEW("미리보기");

    private final String label;
}
