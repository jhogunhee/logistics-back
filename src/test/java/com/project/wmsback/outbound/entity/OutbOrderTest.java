package com.project.wmsback.outbound.entity;

import com.project.mdm.store.entity.Store;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 출고주문 헤더의 상태 전이. 할당은 웨이브를 대상으로 실행하지만 <b>상태는 주문 단위</b>로 움직이고,
 * 부분할당 여부는 상태가 아니라 수량 비교로 파생시킨다.
 *
 * <p>여기서 지키려는 것은 두 가지다 — ① 할당이 여러 번 실행돼도 상태가 흔들리지 않을 것(멱등)
 * ② 전량 해제하면 되돌릴 수 있는 구간(확정취소·웨이브 빼기)이 <b>다시 열릴 것</b>.
 * ②가 깨지면 상태는 ALLOCATED인데 할당이 0건인 주문이 영영 고착된다.
 *
 * <p>{@code PICKING} 이상에서 막히는 분기는 여기서 검증하지 않는다 — 그 상태로 보내는 메서드가
 * 아직 없어 리플렉션 없이는 만들 수 없고, 피킹 구현 시 그쪽 테스트가 자연히 덮는다.
 */
class OutbOrderTest {

    private OutbOrder order() {
        return OutbOrder.builder()
                .outbNo("OB-20260803-001")
                .omsOutbOrderId(1L)
                .store(mock(Store.class))
                .odrDe(LocalDate.of(2026, 8, 3))
                .expctDe(LocalDate.of(2026, 8, 4))
                .outbTyp("NRML")
                .build();
    }

    private OutbWave plannedWave() {
        return OutbWave.builder().wavNo("WV-20260803-001").build();
    }

    @Nested
    @DisplayName("recalcStatus() — 사실에서 상태를 다시 계산한다")
    class RecalcStatus {

        @Test
        @DisplayName("첫 할당이 CREATED를 ALLOCATED로 옮긴다 — 실적 0이면 아직 집히지 않은 것이다")
        void firstAllocation() {
            OutbOrder order = order();
            assertEquals(OutbStatus.CREATED, order.getStatus());

            order.recalcStatus(1, 1, 0);

            assertEquals(OutbStatus.ALLOCATED, order.getStatus());
        }

        @Test
        @DisplayName("같은 사실로 여러 번 불러도 상태가 흔들리지 않는다 — 부분할당 뒤 재할당이 같은 경로다")
        void idempotent() {
            OutbOrder order = order();
            order.recalcStatus(1, 1, 0);

            assertDoesNotThrow(() -> order.recalcStatus(2, 2, 0));
            assertDoesNotThrow(() -> order.recalcStatus(2, 2, 0));

            assertEquals(OutbStatus.ALLOCATED, order.getStatus());
        }

        @Test
        @DisplayName("실적이 붙으면 PICKING — 과거 상태가 아니라 실적 있는 할당 수가 가른다")
        void picking() {
            OutbOrder order = order();

            order.recalcStatus(2, 1, 1);

            assertEquals(OutbStatus.PICKING, order.getStatus());
        }

        @Test
        @DisplayName("전 할당이 소진되면 PICKED")
        void picked() {
            OutbOrder order = order();

            order.recalcStatus(2, 0, 2);

            assertEquals(OutbStatus.PICKED, order.getStatus());
        }

        /**
         * 치명적이었던 빈 칸 — 할당 둘 중 하나는 집품 완료, 다른 하나는 지시취소 후 해제되면
         * 남은 할당은 이미 소진됐는데 집을 것도 결품 종결할 것도 없다. 「0건이 됐나」만 묻던
         * 옛 판정은 여기서 아무 일도 하지 않아 주문을 PICKING에 영구히 고이게 했다.
         */
        @Test
        @DisplayName("소진된 할당만 남으면 PICKED로 닫힌다 — 해제가 남긴 고임을 막는다")
        void closesWhenOnlyPickedAllocsRemain() {
            OutbOrder order = order();
            order.recalcStatus(2, 1, 1);
            assertEquals(OutbStatus.PICKING, order.getStatus());

            order.recalcStatus(1, 0, 1);

            assertEquals(OutbStatus.PICKED, order.getStatus());
        }

        /**
         * 결품 종결이 잔량을 사후에 키우고 그 잔량이 재할당되면, PICKED의 전제(전 할당 소진)가
         * 실제로 거짓이 된다. 되돌림은 새 규칙이 아니라 재산출의 자연스러운 답이다.
         */
        @Test
        @DisplayName("PICKED 주문에 할당이 붙으면 PICKING으로 되돌아온다")
        void reopensWhenAllocationIsAddedAfterPicked() {
            OutbOrder order = order();
            order.recalcStatus(1, 0, 1);
            assertEquals(OutbStatus.PICKED, order.getStatus());

            order.recalcStatus(2, 1, 1);

            assertEquals(OutbStatus.PICKING, order.getStatus());
        }

        @Test
        @DisplayName("할당이 0건이 되면 CREATED로 돌아간다")
        void revertsToCreated() {
            OutbOrder order = order();
            order.recalcStatus(1, 1, 0);

            order.recalcStatus(0, 0, 0);

            assertEquals(OutbStatus.CREATED, order.getStatus());
        }

        @Test
        @DisplayName("이미 CREATED면 아무 일도 하지 않는다 — 해제 때마다 부르는 자리라 조건을 밖에 두지 않는다")
        void revertIdempotent() {
            OutbOrder order = order();

            assertDoesNotThrow(() -> order.recalcStatus(0, 0, 0));

            assertEquals(OutbStatus.CREATED, order.getStatus());
        }
    }

    @Nested
    @DisplayName("되돌릴 수 있는 구간이 다시 열린다")
    class ReopensRevertPaths {

        @Test
        @DisplayName("전량 해제하면 확정취소가 다시 가능해진다")
        void revertibleAgain() {
            OutbOrder order = order();
            order.recalcStatus(1, 1, 0);
            assertThrows(IllegalStateException.class, order::requireRevertible);

            order.recalcStatus(0, 0, 0);

            assertDoesNotThrow(order::requireRevertible);
        }

        @Test
        @DisplayName("전량 해제하면 웨이브에서 뺄 수 있게 된다")
        void unassignableAgain() {
            OutbOrder order = order();
            order.assignWave(plannedWave(), WavRegTyp.MANUAL);
            order.recalcStatus(1, 1, 0);
            assertThrows(IllegalStateException.class, order::unassignWave);

            order.recalcStatus(0, 0, 0);
            order.unassignWave();

            assertNull(order.getWave());
            assertNull(order.getWavRegTyp());
        }
    }

    @Nested
    @DisplayName("할당된 주문은 웨이브 편성을 건드릴 수 없다")
    class WaveGuardsStayClosed {

        @Test
        @DisplayName("확정취소가 막힌다 — 할당 해제가 먼저다")
        void cannotRevert() {
            OutbOrder order = order();
            order.recalcStatus(1, 1, 0);

            assertThrows(IllegalStateException.class, order::requireRevertible);
        }

        @Test
        @DisplayName("웨이브에서 뺄 수 없다 — 빼면 그 할당이 어느 피킹지시에도 속하지 않는 미아가 된다")
        void cannotUnassign() {
            OutbOrder order = order();
            order.assignWave(plannedWave(), WavRegTyp.MANUAL);
            order.recalcStatus(1, 1, 0);

            assertThrows(IllegalStateException.class, order::unassignWave);
        }

        @Test
        @DisplayName("웨이브에 담을 수도 없다 — 할당은 편성된 뒤에만 일어나므로 이 순서 자체가 없다")
        void cannotAssign() {
            OutbOrder order = order();
            order.recalcStatus(1, 1, 0);

            assertThrows(IllegalStateException.class,
                    () -> order.assignWave(plannedWave(), WavRegTyp.MANUAL));
        }
    }
}
