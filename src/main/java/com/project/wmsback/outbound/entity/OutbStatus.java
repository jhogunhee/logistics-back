package com.project.wmsback.outbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 출고 워크플로 상태. 부분할당 여부는 상태가 아니라 라인/할당 수량에서 파생한다.
 * CREATED → ALLOCATED → PICKING → PICKED → SHIPPED / CANCELLED(피킹 시작 전만)
 */
@Getter
@RequiredArgsConstructor
public enum OutbStatus {
    CREATED("생성"),
    ALLOCATED("할당"),
    PICKING("피킹중"),
    PICKED("피킹완료"),
    SHIPPED("출고확정"),
    CANCELLED("취소");

    private final String label;
}
