package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 이력의 참조 문서 유형. 수동 조정(ADJUST)은 참조 문서가 없을 수 있다.
 */
@Getter
@RequiredArgsConstructor
public enum RefDocType {
    INBOUND("입고 문서"),
    OUTBOUND("출고 문서");

    private final String label;
}
