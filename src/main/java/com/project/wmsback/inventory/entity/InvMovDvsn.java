package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이동구분. 재고이동 화면의 등록은 INV_MOV 고정이고, 그 화면의 확정·취소도 INV_MOV만 허용한다 —
 * 적치·피킹 유형은 각자의 경로 전용이다 — 재고이동 화면은 재고이동 유형만 확정·취소할 수 있다.
 * 적치·피킹 지시를 inv_mov_task로 통합할지(별도 putaway_task 유지 여부)는 각 지시 구현 시 결정한다.
 */
@Getter
@RequiredArgsConstructor
public enum InvMovDvsn {
    INV_MOV("재고이동"),
    PTAWY("적치"),
    PIKNG("피킹");

    private final String label;
}
