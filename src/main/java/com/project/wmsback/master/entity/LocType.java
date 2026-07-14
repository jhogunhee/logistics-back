package com.project.wmsback.master.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 로케이션 유형. STAGE는 적치 대기 재고가 머무는 곳이라 할당 대상에서 제외된다.
 */
@Getter
@RequiredArgsConstructor
public enum LocType {
    STAGE("입고 스테이징(적치 대기)"),
    STORAGE("보관(할당 대상)");

    private final String label;
}
