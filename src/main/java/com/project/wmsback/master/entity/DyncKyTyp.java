package com.project.wmsback.master.entity;

/** 채번 규칙의 동적키 유형. NONE=카운터 전역 공유 / DATE=호출자가 넘긴 날짜 기준으로 카운터 분리 */
public enum DyncKyTyp {
    NONE,
    DATE
}
