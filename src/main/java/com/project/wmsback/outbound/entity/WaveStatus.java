package com.project.wmsback.outbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 출고 웨이브 상태. 릴리즈(=재고 할당) 이후의 진행(피킹/확정)은 주문 단위라
 * 웨이브 상태는 편성/릴리즈 두 단계뿐이다.
 * PLANNED(편성중, 주문 담기 가능) → RELEASED(릴리즈 완료)
 */
@Getter
@RequiredArgsConstructor
public enum WaveStatus {
    PLANNED("편성중"),
    RELEASED("릴리즈");

    private final String label;
}
