package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 이력 유형. 물리적 변동만 존재한다 (할당은 물리 이동이 아니므로 없음).
 */
@Getter
@RequiredArgsConstructor
public enum TxType {
    RECEIVE("입고"),
    MOVE("이동(적치 포함)"),
    ADJUST("조정"),
    PICK("피킹"),
    SHIP("출고확정");

    private final String label;
}
