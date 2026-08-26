package com.project.wmsback.inbound.entity;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
import com.project.mdm.vendor.entity.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 입고 헤더의 상태 전이와 5단계 진행 파생.
 *
 * <p>여기서 지키려는 것은 두 가지다 — ① <b>종결은 입고확정(confirm) 하나뿐</b>이고 자동 전이가 없다:
 * 전량 검수돼도, 전량 적치돼도 상태는 RECEIVING에 머문다. ② 확정의 전제조건은 「온 것은 전부 적치」
 * (전 라인 ptawy == rcvd)이고, 결품(예정-검수)은 확정이 못박는다 — 80/100만 오고 끝난 입고가 그 사례다.
 *
 * <p>5단계 진행({@link IbPrgr})은 저장값이 아니라 파생값이므로, 여기서는 수량 조합별 분기와
 * 판정 순서(검수 0건이 「전량 적치」로 헛통과하지 않는 것)를 본다.
 */
class IbOrderTest {

    private IbOrder order(long... expctQtys) {
        IbOrder order = IbOrder.builder()
                .ibNo("IB-20260814-001")
                .omsIbOrderId(1L)
                .vendor(mock(Vendor.class))
                .expctDe(LocalDate.of(2026, 8, 14))
                .odrDvsn("NRML")
                .build();
        for (long expctQty : expctQtys) {
            order.addLine(IbLine.builder().prod(mock(Prod.class)).expctQty(expctQty).build());
        }
        return order;
    }

    private IbLine line(IbOrder order, int idx) {
        return order.getLines().get(idx);
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("검수 전(SCHEDULED)에는 확정할 수 없다 — 취소는 OMS 확정취소의 소관")
        void rejectsScheduled() {
            IbOrder order = order(100);

            assertThrows(IllegalStateException.class, order::confirm);
        }

        @Test
        @DisplayName("적치가 덜 끝났으면(ptawy < rcvd) 확정할 수 없다")
        void rejectsUnfinishedPutaway() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(80);
            line(order, 0).putaway(50);

            assertThrows(IllegalStateException.class, order::confirm);
            assertEquals(IbStatus.RECEIVING, order.getStatus());
        }

        @Test
        @DisplayName("80/100만 오고 전부 적치됐으면 확정된다 — 결품 20은 이 시점에 못박힌다")
        void confirmsPartialReceiptWithShortage() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(80);
            line(order, 0).putaway(80);

            order.confirm();

            assertEquals(IbStatus.CONFIRMED, order.getStatus());
            assertNotNull(order.getCfmDt());
        }

        @Test
        @DisplayName("이미 확정된 입고는 다시 확정할 수 없다")
        void rejectsReconfirm() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(100);
            line(order, 0).putaway(100);
            order.confirm();

            assertThrows(IllegalStateException.class, order::confirm);
        }

        @Test
        @DisplayName("전량 검수취소로 rcvd가 0이어도 확정된다 (전량 결품) — 막으면 이 입고는 영구 고착된다")
        void confirmsZeroReceivedOrder() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(80);
            line(order, 0).cancelReceive(80);

            assertDoesNotThrow(order::confirm);
            assertEquals(IbStatus.CONFIRMED, order.getStatus());
        }
    }

    @Nested
    @DisplayName("자동 전이가 없다")
    class NoAutoTransition {

        @Test
        @DisplayName("전량 검수돼도 상태는 RECEIVING에 머문다")
        void staysReceivingAfterFullReceipt() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(100);

            assertEquals(IbStatus.RECEIVING, order.getStatus());
            assertNull(order.getCfmDt());
        }

        @Test
        @DisplayName("전량 적치돼도 상태는 RECEIVING에 머문다 — 종결은 confirm뿐")
        void staysReceivingAfterFullPutaway() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(100);
            line(order, 0).putaway(100);

            assertEquals(IbStatus.RECEIVING, order.getStatus());
        }
    }

    @Nested
    @DisplayName("IbLine.progressStatus() — 라인 5단계 파생")
    class LineProgress {

        @Test
        @DisplayName("검수 전 SCHEDULED / 지시 전 RECEIVING / 다 옮김 PTAWY_CMPL")
        void quantityBranches() {
            IbOrder order = order(100, 100, 100, 100);
            order.startReceiving();
            line(order, 1).receive(30);                          // 덜 옴, 지시 없음
            line(order, 2).receive(100);                         // 다 왔지만 지시 전
            line(order, 3).receive(100);
            line(order, 3).putaway(100);                         // 다 오고 다 옮김

            assertEquals(IbPrgr.SCHEDULED, line(order, 0).progressStatus(false));
            assertEquals(IbPrgr.RECEIVING, line(order, 1).progressStatus(false));
            // 지시가 없으면 다 왔어도 아직 「검수」다 — 예전엔 여기서 PTAWY_DRCT가 나와
            // 지시가 존재하지도 않는데 화면이 「적치지시」라고 말했다
            assertEquals(IbPrgr.RECEIVING, line(order, 2).progressStatus(false));
            assertEquals(IbPrgr.PTAWY_CMPL, line(order, 3).progressStatus(false));
        }

        @Test
        @DisplayName("지시가 나가면 부분 검수여도 PTAWY_DRCT — 헤더가 말하는 것과 같아야 한다")
        void openDirectiveMovesLineForward() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(40);                          // 40만 옴

            assertEquals(IbPrgr.RECEIVING, line(order, 0).progressStatus(false));
            assertEquals(IbPrgr.PTAWY_DRCT, line(order, 0).progressStatus(true));
        }

        @Test
        @DisplayName("일부라도 옮겼으면 지시가 닫혔어도 PTAWY_DRCT")
        void partialPutawayKeepsDirectiveStage() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(100);
            line(order, 0).putaway(40);                          // 40만 옮김, 지시는 완료됨

            assertEquals(IbPrgr.PTAWY_DRCT, line(order, 0).progressStatus(false));
        }

        @Test
        @DisplayName("온 걸 다 옮겼으면 부분 검수여도 PTAWY_CMPL — 확정 눌러도 되는 상태다")
        void partiallyReceivedButFullyPutaway() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(40);
            line(order, 0).putaway(40);                          // 온 40을 다 옮김 (예정 100)

            assertEquals(IbPrgr.PTAWY_CMPL, line(order, 0).progressStatus(false));
        }

        @Test
        @DisplayName("헤더가 확정되면 결품 라인도 CONFIRMED — 닫힌 문서의 라인이 「검수중」으로 보이면 안 된다")
        void confirmedHeaderWins() {
            IbOrder order = order(100, 100);
            order.startReceiving();
            line(order, 0).receive(80);
            line(order, 0).putaway(80);                          // 결품 20
            order.confirm();

            assertEquals(IbPrgr.CONFIRMED, line(order, 0).progressStatus(false));
            assertEquals(IbPrgr.CONFIRMED, line(order, 1).progressStatus(false));
        }
    }

    @Nested
    @DisplayName("requireRevertible() — OMS 확정취소 가드")
    class RequireRevertible {

        @Test
        @DisplayName("검수 전(SCHEDULED)에는 취소할 수 있다")
        void revertibleWhenScheduled() {
            assertDoesNotThrow(order(100)::requireRevertible);
        }

        @Test
        @DisplayName("검수가 시작되면 취소할 수 없다")
        void notRevertibleAfterReceiving() {
            IbOrder order = order(100);
            order.startReceiving();

            assertThrows(IllegalStateException.class, order::requireRevertible);
        }
    }

    @Nested
    @DisplayName("상대처 — 구분과 짝이 맞아야 한다")
    class Partner {

        private IbOrder.IbOrderBuilder base() {
            return IbOrder.builder().ibNo("IB-1").omsIbOrderId(1L).expctDe(LocalDate.of(2026, 8, 25));
        }

        @Test
        @DisplayName("정상 입고는 벤더, 반품입고는 점포")
        void vendorForNormalStoreForRtngs() {
            assertDoesNotThrow(() -> base().vendor(mock(Vendor.class)).odrDvsn("NRML").build());
            IbOrder rtngs = base().store(mock(Store.class)).odrDvsn("RTNGS").build();
            assertTrue(rtngs.isRtngs());
        }

        @Test
        @DisplayName("반품인데 벤더 / 정상인데 점포 / 둘 다 / 둘 다 아님은 거부")
        void rejectsMismatch() {
            assertThrows(IllegalArgumentException.class, () -> base().vendor(mock(Vendor.class)).odrDvsn("RTNGS").build());
            assertThrows(IllegalArgumentException.class, () -> base().store(mock(Store.class)).odrDvsn("NRML").build());
            assertThrows(IllegalArgumentException.class, () -> base().vendor(mock(Vendor.class)).store(mock(Store.class)).odrDvsn("NRML").build());
            assertThrows(IllegalArgumentException.class, () -> base().odrDvsn("NRML").build());
        }

        @Test
        @DisplayName("검수 단위 — 정상은 입고단위, 반품은 출고단위")
        void rcvUomCd() {
            Prod prod = mock(Prod.class);
            when(prod.getInbUomCd()).thenReturn("BOX");
            when(prod.getOutbUomCd()).thenReturn("EA");
            assertEquals("BOX", base().vendor(mock(Vendor.class)).odrDvsn("NRML").build().rcvUomCd(prod));
            assertEquals("EA", base().store(mock(Store.class)).odrDvsn("RTNGS").build().rcvUomCd(prod));
        }
    }

    @Nested
    @DisplayName("IbLine — 불량(rjct)")
    class LineReject {

        @Test
        @DisplayName("불량만 온 라인은 예정이 아니다 — 적치할 양품이 0이라 이미 확정 대기(PTAWY_CMPL)다")
        void rjctCountsForProgress() {
            IbOrder order = order(100, 100);
            order.startReceiving();
            line(order, 0).reject(30);                 // 불량만 — 적치할 게 없다
            line(order, 1).receive(60);
            line(order, 1).reject(40);                 // 양품 60 + 불량 40, 아직 안 옮김

            assertEquals(IbPrgr.PTAWY_CMPL, line(order, 0).progressStatus(false));
            assertEquals(IbPrgr.RECEIVING, line(order, 1).progressStatus(false));
            assertEquals(30L, line(order, 0).getRjctQty());
        }

        @Test
        @DisplayName("불량은 적치 대상이 아니다 — 양품만 적치되면 확정된다")
        void confirmIgnoresRjct() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).receive(60);
            line(order, 0).reject(40);
            line(order, 0).putaway(60);

            assertDoesNotThrow(order::confirm);
        }

        @Test
        @DisplayName("불량 취소는 rjct만 줄인다")
        void cancelReject() {
            IbOrder order = order(100);
            order.startReceiving();
            line(order, 0).reject(40);
            line(order, 0).cancelReject(40);

            assertEquals(0L, line(order, 0).getRjctQty());
            assertEquals(IbPrgr.SCHEDULED, line(order, 0).progressStatus(false));
        }
    }
}
