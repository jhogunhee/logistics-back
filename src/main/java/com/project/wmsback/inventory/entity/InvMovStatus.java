package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이동지시 상태. 워크플로 단계만 표현한다 — 부분확정 여부는 수량(cmplQty vs drctQty)에서 파생한다.
 * putaway_task와 동일한 상태 집합 (DIRECTED / DONE / CANCELLED).
 */
@Getter
@RequiredArgsConstructor
public enum InvMovStatus {
    DIRECTED("지시"),
    DONE("완료"),
    CANCELLED("취소");

    private final String label;
}
