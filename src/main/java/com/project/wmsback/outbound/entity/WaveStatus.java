package com.project.wmsback.outbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 출고 웨이브 상태. 웨이브는 피킹지시의 <b>발행 단위</b>이고 발행 이후의 진행(피킹/확정)은
 * 전부 주문 단위로 흐르므로, 웨이브 상태는 편성/발행 두 단계뿐이다.
 * PLANNED(편성중, 주문 담기 가능) → ISSUED(피킹지시 발행 완료)
 */
@Getter
@RequiredArgsConstructor
public enum WaveStatus {
    PLANNED("편성중"),
    ISSUED("지시발행");

    private final String label;
}
