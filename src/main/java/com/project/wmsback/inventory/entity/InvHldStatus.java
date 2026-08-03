package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 보류 건 상태. 2값뿐 — 오등록 취소는 별도 상태 없이 해제(사유: 오등록)로 흡수한다.
 * 보류는 등록 즉시 발효라 putaway_task의 CANCELLED(실행 전에 접은 지시)에 해당하는 구간이 없다.
 * 부분 해제 여부는 수량(rlzQty vs hldQty)에서 파생한다.
 */
@Getter
@RequiredArgsConstructor
public enum InvHldStatus {
    HELD("보류중"),
    RELEASED("해제");

    private final String label;
}
