package com.project.wmsback.inbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 입고 워크플로 상태. 부분입고 여부는 상태가 아니라 라인 수량에서 파생한다.
 * SCHEDULED → RECEIVING → RECEIVED → COMPLETED / CANCELLED(검수 시작 전만)
 */
@Getter
@RequiredArgsConstructor
public enum IbStatus {
    SCHEDULED("입고예정"),
    RECEIVING("입고중"),
    RECEIVED("입고마감"),
    COMPLETED("적치완료"),
    CANCELLED("취소");

    private final String label;
}
