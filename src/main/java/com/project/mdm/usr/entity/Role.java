package com.project.mdm.usr.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 역할. 업무 구역(URL 접두)과 1:1이라 목록을 마스터 테이블이 아니라 여기에 고정한다 —
 * 데이터로 늘려도 그 접두를 아는 {@code SecurityConfig}가 함께 바뀌어야 하므로 두 벌이 된다.
 *
 * <p>값 목록은 {@code usr_role.ck_usr_role} CHECK와 맞춘다.
 */
@Getter
@RequiredArgsConstructor
public enum Role {
    ADMR("시스템관리자"),
    CENT_ADMR("센터관리자"),
    ODR_PIC("주문담당"),
    IB_PIC("입고담당"),
    INV_PIC("재고담당"),
    OUTB_PIC("출고담당"),
    INQ("조회전용");

    private final String label;
}
