package com.project.mdm.nbr.entity;

import lombok.Getter;

/**
 * 채번 규칙의 리셋 단위. NONE=카운터 전역 공유 / YEAR·MONTH·DAY=호출자가 넘긴 날짜를
 * 해당 단위로 자른 값 기준으로 카운터를 분리한다. 화면에 찍히는 날짜 조각 포맷도
 * 이 값에서 그대로 파생된다(dyncKyPattern) — 리셋 기준과 표시 포맷이 항상 일치한다.
 */
@Getter
public enum DyncKyTyp {
    NONE(null),
    YEAR("yyyy"),
    MONTH("yyyyMM"),
    DAY("yyyyMMdd");

    private final String dyncKyPattern;

    DyncKyTyp(String dyncKyPattern) {
        this.dyncKyPattern = dyncKyPattern;
    }

    public boolean isDateBased() {
        return this != NONE;
    }
}
