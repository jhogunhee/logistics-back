package com.project.omsback.inbound.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 자동발주 수량 산식 — 올림 환산 · 최소주문수량 · 발주 대상 경계. */
class AtoOdrQtyCalcTest {

    @Nested
    @DisplayName("순재고와 발주 대상 판정")
    class Net {

        @Test
        @DisplayName("순재고 = 가용 + 미입고 예정 + 미확정 발주 — 세 항을 다 세야 어제 낸 발주를 또 내지 않는다")
        void netSumsThreeTerms() {
            assertEquals(150, AtoOdrQtyCalc.net(50, 40, 60));
            assertEquals(0, AtoOdrQtyCalc.net(0, 0, 0));
        }

        @Test
        @DisplayName("발주점과 같으면 아직 대상이 아니다 — 미만일 때만 시킨다")
        void equalToMinIsNotShort() {
            assertTrue(AtoOdrQtyCalc.isShort(99, 100));
            assertFalse(AtoOdrQtyCalc.isShort(100, 100));
            assertFalse(AtoOdrQtyCalc.isShort(101, 100));
        }
    }

    @Nested
    @DisplayName("발주 수량")
    class ProposedQty {

        @Test
        @DisplayName("부족분을 입고단위로 올림한다 — 내림이면 상한에 영영 닿지 못한다")
        void roundsUpToInboundUom() {
            assertEquals(2, AtoOdrQtyCalc.proposedQty(25, 24, 1));   // 24개들이 2박스 = 48 (1박스면 1개 모자라다)
            assertEquals(1, AtoOdrQtyCalc.proposedQty(24, 24, 1));   // 딱 떨어지면 그대로
            assertEquals(1, AtoOdrQtyCalc.proposedQty(1, 24, 1));
        }

        @Test
        @DisplayName("낱개가 입고단위면 부족분이 곧 발주 수량이다")
        void eachUnitPassesThrough() {
            assertEquals(37, AtoOdrQtyCalc.proposedQty(37, 1, 1));
        }

        @Test
        @DisplayName("최소주문수량이 올림 결과보다 크면 그쪽을 쓴다 — 상한을 넘겨도 자르지 않는다")
        void minOrderQtyWins() {
            assertEquals(5, AtoOdrQtyCalc.proposedQty(25, 24, 5));   // 올림 2 < MOQ 5
            assertEquals(3, AtoOdrQtyCalc.proposedQty(60, 24, 2));   // 올림 3 > MOQ 2 → 올림 그대로
        }

        @Test
        @DisplayName("부족분이 0 이하면 발주하지 않는다")
        void noOrderWhenNotShort() {
            assertEquals(0, AtoOdrQtyCalc.proposedQty(0, 24, 5));
            assertEquals(0, AtoOdrQtyCalc.proposedQty(-10, 24, 5));
        }
    }

    @Test
    @DisplayName("입고단위 낱개수량이 0 이하면 환산이 성립하지 않아 거부한다")
    void rejectsNonPositiveEaQty() {
        assertThrows(IllegalArgumentException.class, () -> AtoOdrQtyCalc.ceilDiv(10, 0));
    }
}
