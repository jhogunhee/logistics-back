package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고조사 상태. 워크플로 단계만 표현한다 — 「부분확정」은 두지 않는다(라인 입력 진행도는 수량에서 파생).
 * 확정 후 재정정은 조사를 되열지 않고 새 조사를 만든다 (append-only 원칙).
 */
@Getter
@RequiredArgsConstructor
public enum InvStktkStatus {
    CREATED("작성"),
    CONFIRMED("확정"),
    CANCELLED("취소");

    private final String label;
}
