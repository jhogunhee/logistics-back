package com.project.wmsback.strategy.core.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 전략 유형. DB CHECK(ck_stgy_rvsn_typ 등)와 값을 맞춘다. WAV·ALOC은 2차 선반영 */
@Getter
@RequiredArgsConstructor
public enum StgyTyp {
    INSP("검수"),
    PTAWY("적치"),
    WAV("웨이브"),
    ALOC("할당");

    private final String label;
}
