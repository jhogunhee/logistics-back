package com.project.wmsback.warehouse.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 로케이션 유형. STAGE는 실물이 잠깐 머무는 곳이다 — 입고(RCV-STAGE, 적치 대기)와
 * 출고(SHIP-STAGE, 반출 대기) 둘 다 이 유형이고, 할당·보류·실사·이동의 대상에서 제외된다.
 */
@Getter
@RequiredArgsConstructor
public enum LocTyp {
    STAGE("스테이징(적치 대기 · 반출 대기)"),
    STORAGE("보관(할당 대상)");

    private final String label;
}
