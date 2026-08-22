package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이동구분 — 「보관 → 보관」 2단계 지시를 한 테이블에 담는 두 갈래. 재고이동 화면의 등록은 INV_MOV 고정이고,
 * 그 화면의 확정·취소도 INV_MOV만 허용한다. 적치(putaway_task, 2026-08-04)와 피킹(pikng_task, 2026-08-20)은
 * 각자 테이블이라 PIKNG·PTAWY 값은 제거했다(PTAWY는 2026-08-22) — 어느 코드도 쓰지 않는 값을 선택지로 남기지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum InvMovDvsn {
    INV_MOV("재고이동"),
    /** 수시보충 — 피킹지시 발행이 보관존 할당분에 짝으로 낸다. 예약은 잡지 않고(할당이 든다) 확정 경로도 따로다 */
    RPLN("수시보충");

    private final String label;
}
