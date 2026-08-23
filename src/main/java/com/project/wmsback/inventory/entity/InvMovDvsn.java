package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이동구분. 재고이동 화면의 등록은 INV_MOV 고정, 보충 화면의 발행은 SPMT 고정이고,
 * 확정·취소는 두 유형 모두 이동지시 관리 화면이 처리한다 — 실물을 옮기는 동일 작업이라 경로를 가르지 않는다.
 * 적치는 별도 putaway_task 유지로 확정됐고(2026-08-04), 피킹도 별도 pikng_task로 확정되어
 * (2026-08-20) PIKNG 값은 제거했다 — 어느 코드도 쓰지 않는 값을 선택지로 남기지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum InvMovDvsn {
    INV_MOV("재고이동"),
    PTAWY("적치"),
    /** 보충 — 피킹존 고정로케이션(fxng_loc)을 min 미달 시 max까지 채우는 보관→피킹 이동 (2026-08-21) */
    SPMT("보충");

    private final String label;
}
