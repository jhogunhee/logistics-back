package com.project.wmsback.master.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 보관유형. 존의 물리 보관 형태.
 * 값 목록은 공통코드 STRG_TYP과 같아야 한다 (화면 드롭다운이 공통코드를 읽는다).
 */
@Getter
@RequiredArgsConstructor
public enum StrgTyp {
    RACK("랙"),
    FLAT("평치"),
    VRTL("가상");

    private final String label;
}
