package com.project.omsback.inbound.entity;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
import com.project.mdm.vendor.entity.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 입고주문의 상대처 — 구분이 반품이면 점포, 아니면 벤더. 그 짝은 엔티티가 지킨다(DB CHECK는 둘 중 하나까지만). */
class OmsIbOrderTest {

    private OmsIbOrder.OmsIbOrderBuilder base() {
        return OmsIbOrder.builder().omsIbNo("PO-1").expctDe(LocalDate.of(2026, 8, 25));
    }

    @Test
    @DisplayName("정상은 벤더, 반품은 점포 + 원 출고번호(선택)")
    void partnerByDvsn() {
        assertDoesNotThrow(() -> base().vendor(mock(Vendor.class)).odrDvsn("NRML").build());
        OmsIbOrder rtngs = base().store(mock(Store.class)).odrDvsn("RTNGS").refOutbNo("OB-20260820-001").build();
        assertEquals("OB-20260820-001", rtngs.getRefOutbNo());
        assertNull(rtngs.getVendor());
    }

    @Test
    @DisplayName("짝이 어긋나면 거부 — 생성도 수정도")
    void rejectsMismatch() {
        assertThrows(IllegalArgumentException.class, () -> base().vendor(mock(Vendor.class)).odrDvsn("RTNGS").build());
        assertThrows(IllegalArgumentException.class, () -> base().store(mock(Store.class)).odrDvsn("NRML").build());

        OmsIbOrder order = base().vendor(mock(Vendor.class)).odrDvsn("NRML").build();
        assertThrows(IllegalArgumentException.class, () ->
                order.update(mock(Vendor.class), null, null, LocalDate.of(2026, 8, 26), "RTNGS", null, null, List.of()));
    }

    @Test
    @DisplayName("정상 발주에는 원 출고번호를 두지 않는다")
    void refOutbNoOnlyForRtngs() {
        OmsIbOrder order = base().vendor(mock(Vendor.class)).odrDvsn("NRML").refOutbNo("OB-1").build();
        assertNull(order.getRefOutbNo());
    }

    @Test
    @DisplayName("발주 단위 — 정상은 입고단위, 반품은 출고단위")
    void odrUomCd() {
        Prod prod = mock(Prod.class);
        when(prod.getInbUomCd()).thenReturn("BOX");
        when(prod.getOutbUomCd()).thenReturn("EA");
        assertEquals("BOX", base().vendor(mock(Vendor.class)).odrDvsn("NRML").build().odrUomCd(prod));
        assertEquals("EA", base().store(mock(Store.class)).odrDvsn("RTNGS").build().odrUomCd(prod));
    }
}
