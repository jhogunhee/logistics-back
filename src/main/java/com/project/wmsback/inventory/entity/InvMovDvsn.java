package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이동구분. 재고이동 화면의 등록은 INV_MOV 고정이고, 그 화면의 확정·취소도 INV_MOV만 허용한다.
 * 적치는 별도 putaway_task 유지로 확정됐고(2026-08-04), 피킹도 별도 pikng_task로 확정되어
 * (2026-08-20) PIKNG 값은 제거했다 — 어느 코드도 쓰지 않는 값을 선택지로 남기지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum InvMovDvsn {
    INV_MOV("재고이동"),
    PTAWY("적치");

    private final String label;
}
