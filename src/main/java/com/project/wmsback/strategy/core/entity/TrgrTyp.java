package com.project.wmsback.strategy.core.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 전략 실행 트리거. AUTO는 2차 웨이브 자동실행용 선반영, PREVIEW는 1차 미기록 */
@Getter
@RequiredArgsConstructor
public enum TrgrTyp {
    MANUAL("화면 조작"),
    AUTO("자동 실행"),
    PREVIEW("미리보기");

    private final String label;
}
