package com.project.wmsback.master.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 업무구분. 존이 담당하는 업무.
 * 값 목록은 공통코드 BIZ_DVSN과 같아야 한다 (화면 드롭다운이 공통코드를 읽는다).
 */
@Getter
@RequiredArgsConstructor
public enum BizDvsn {
    INB("입고작업"),
    OUTB("출고작업"),
    STRG("보관"),
    PIKNG("피킹"),
    RTNGS("반품"),
    WRK("작업");

    private final String label;
}
