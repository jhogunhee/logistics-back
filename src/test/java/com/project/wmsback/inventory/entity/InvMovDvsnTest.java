package com.project.wmsback.inventory.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 이동구분이 스스로 드는 속성 — 채번규칙(번호 접두가 이력의 유일한 문서 구분자라 구분값과 따로 다니면 안 된다)과
 * 「등록 시 예약을 드는가」(이동지시 관리 화면의 확정·취소 허용 여부가 여기서 나온다).
 */
class InvMovDvsnTest {

    @Test
    @DisplayName("채번규칙은 구분값에 붙어 있다 — 정기보충만 SP- 전용, 나머지는 이동지시 번호")
    void noRuleCdIsBoundToDvsn() {
        assertEquals("INV_MOV_NO", InvMovDvsn.INV_MOV.getNoRuleCd());
        assertEquals("INV_MOV_NO", InvMovDvsn.RPLN.getNoRuleCd());
        assertEquals("SPMT_NO", InvMovDvsn.SPMT.getNoRuleCd());
    }

    @Test
    @DisplayName("예약을 드는 구분만 이동지시 관리 화면이 확정·취소한다 — 수시보충은 예약의 주인이 할당이라 제외")
    void reservingDvsnIsScreenManaged() {
        assertTrue(InvMovDvsn.INV_MOV.isReserving());
        assertTrue(InvMovDvsn.SPMT.isReserving());
        assertFalse(InvMovDvsn.RPLN.isReserving());
    }
}
