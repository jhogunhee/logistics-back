package com.project.wmsback.outbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.outbound.dto.ShmtConfirmRequest;
import com.project.wmsback.outbound.dto.ShmtConfirmResponse;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.entity.WavRegTyp;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LocRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 출고확정의 규칙. 저장소는 목으로 두고 <b>재고 엔티티의 실제 상태</b>(스테이징 실물·예약의 동시 소진,
 * 빈 행 삭제)와 <b>문서 종결</b>(주문 SHIPPED · 웨이브 CLOSED)로 검증한다 — {@code PikngServiceTest}와 같은 방식.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutbShmtServiceTest {

    @Mock OutbOrderRepository outbOrderRepository;
    @Mock PikngTaskRepository pikngTaskRepository;
    @Mock OutbWaveRepository outbWaveRepository;
    @Mock LocRepository locRepository;
    @Mock InvRepository invRepository;
    @Mock InvHistRepository invHistRepository;

    private OutbShmtService outbShmtService;

    private OutbWave wave;
    private Prod prod;
    private Lot lot;
    private Loc shipStage;
    /** 스테이징 재고 — 피킹이 실물과 예약을 함께 쌓아 둔 행 */
    private Inv staging;
    private final List<Inv> deleted = new ArrayList<>();
    private final List<OutbOrder> waveOrders = new ArrayList<>();
    private final List<PikngTask> tasks = new ArrayList<>();
    private long seq;

    @BeforeEach
    void setUp() {
        outbShmtService = new OutbShmtService(outbOrderRepository, pikngTaskRepository, outbWaveRepository,
                locRepository, new InvStore(invRepository, invHistRepository));
        deleted.clear();
        waveOrders.clear();
        tasks.clear();
        seq = 0;

        wave = OutbWave.builder().wavNo("WV-20260821-001").build();
        setId(wave, 100L);
        wave.issue();

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        lot = mock(Lot.class);
        when(lot.getId()).thenReturn(7L);
        shipStage = mock(Loc.class);
        when(shipStage.getId()).thenReturn(900L);
        when(shipStage.getLocCd()).thenReturn("SHIP-STAGE");

        staging = Inv.builder().prod(prod).loc(shipStage).lot(lot).build();
        setId(staging, 900L);

        when(outbWaveRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(wave));
        when(locRepository.findByLocCd("SHIP-STAGE")).thenReturn(Optional.of(shipStage));
        when(outbOrderRepository.findAllWithWaveByIds(anyCollection()))
                .thenAnswer(i -> waveOrders.stream()
                        .filter(o -> i.<java.util.Collection<Long>>getArgument(0).contains(o.getId())).toList());
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(waveOrders);
        when(pikngTaskRepository.findLiveWithDetailsByOrderIds(anyCollection(), any()))
                .thenAnswer(i -> tasks.stream()
                        .filter(t -> i.<java.util.Collection<Long>>getArgument(0)
                                .contains(t.getOutbAlloc().getOutbLine().getOutbOrder().getId()))
                        .toList());
        // 스테이징 행 락 — 키가 (prod 1, SHIP-STAGE 900, lot 7)이고 실물이 있을 때만 존재한다
        when(invRepository.findByKeyForUpdate(1L, 900L, 7L))
                .thenAnswer(i -> deleted.contains(staging) ? Optional.empty() : Optional.of(staging));
        org.mockito.Mockito.doAnswer(i -> { deleted.add(i.getArgument(0)); return null; })
                .when(invRepository).delete(any(Inv.class));
    }

    @Test
    @DisplayName("피킹완료 주문 확정 — 스테이징 실물·예약이 함께 빠지고 SHIP 1행, 주문 SHIPPED, 빈 행 삭제")
    void confirmShipsStagingAndClosesOrder() {
        OutbOrder order = pickedOrder(1L, 30);

        ShmtConfirmResponse response = outbShmtService.confirm(request(1L));

        assertEquals(0, staging.getOnHandQty());
        assertEquals(0, staging.getAlocQty());
        assertTrue(deleted.contains(staging));   // 실물·예약 모두 0 → 행 삭제
        assertEquals(OutbStatus.SHIPPED, order.getStatus());
        assertNotNull(order.getShmtDt());
        ArgumentCaptor<InvHist> hist = ArgumentCaptor.forClass(InvHist.class);
        verify(invHistRepository).save(hist.capture());
        assertEquals(TxTyp.SHIP, hist.getValue().getTxTyp());
        assertEquals(-30, hist.getValue().getQty());
        assertEquals("OB-20260821-001", hist.getValue().getRfnDocNo());
        assertEquals(1, response.orderCount());
        assertEquals(30, response.shmtQty());
        assertEquals(0, response.shotgeQty());
    }

    @Test
    @DisplayName("부분 집품(결품 종결) 주문 — 집품분만 나가고 결품은 주문수량 − 집품수량으로 보고한다")
    void confirmReportsShortage() {
        // 주문 30, 집품 25(결품 종결로 aloc·drct가 25까지 내려간 상태)
        OutbOrder order = pickedOrder(1L, 30, 25);

        ShmtConfirmResponse response = outbShmtService.confirm(request(1L));

        assertEquals(OutbStatus.SHIPPED, order.getStatus());
        assertEquals(25, response.shmtQty());
        assertEquals(5, response.shotgeQty());
    }

    @Test
    @DisplayName("할당 0건(CREATED) 주문은 재고 처리 없이 닫힌다 — 갇힌 주문의 마지막 출구")
    void confirmNoStockOrderWithoutInventory() {
        OutbOrder order = createdOrder(2L, 10);

        ShmtConfirmResponse response = outbShmtService.confirm(request(2L));

        assertEquals(OutbStatus.SHIPPED, order.getStatus());
        verify(invHistRepository, never()).save(any());
        assertEquals(1, response.noStockCount());
        assertEquals(0, response.shmtQty());
        assertEquals(10, response.shotgeQty());
    }

    @Test
    @DisplayName("작업중(PICKING · ALLOCATED) 주문이 섞이면 거부한다 — 한 트랜잭션이라 함께 보낸 주문도 롤백된다")
    void rejectsWorkingOrder() {
        OutbOrder picking = pickedOrder(3L, 20);
        picking.recalcStatus(1, 1, 1);   // 미소진 할당이 남은 상태

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> outbShmtService.confirm(request(3L)));

        assertTrue(e.getMessage().contains("출고작업중"));
        assertEquals(OutbStatus.PICKING, picking.getStatus());
        assertEquals(20, staging.getOnHandQty());
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 확정된 주문은 다시 확정할 수 없다")
    void rejectsShippedOrder() {
        pickedOrder(1L, 30);
        outbShmtService.confirm(request(1L));

        assertThrows(IllegalStateException.class, () -> outbShmtService.confirm(request(1L)));
    }

    @Test
    @DisplayName("발행되지 않은(PLANNED) 웨이브의 주문은 확정할 수 없다")
    void rejectsPlannedWave() {
        wave.cancelIssue();
        createdOrder(2L, 10);

        assertThrows(IllegalStateException.class, () -> outbShmtService.confirm(request(2L)));
    }

    @Test
    @DisplayName("웨이브의 주문이 전부 확정되면 웨이브가 CLOSED로 닫힌다")
    void closesWaveWhenAllOrdersShipped() {
        pickedOrder(1L, 30);
        createdOrder(2L, 10);

        ShmtConfirmResponse response = outbShmtService.confirm(request(1L, 2L));

        assertEquals(WaveStatus.CLOSED, wave.getStatus());
        assertNotNull(wave.getClosDt());
        assertEquals(List.of("WV-20260821-001"), response.closedWavNos());
    }

    @Test
    @DisplayName("남은 주문이 있으면 웨이브는 ISSUED에 머문다")
    void keepsWaveIssuedWhileOrdersRemain() {
        pickedOrder(1L, 30);
        OutbOrder remaining = pickedOrder(3L, 20);
        remaining.recalcStatus(1, 1, 1);

        ShmtConfirmResponse response = outbShmtService.confirm(request(1L));

        assertEquals(WaveStatus.ISSUED, wave.getStatus());
        assertNull(wave.getClosDt());
        assertTrue(response.closedWavNos().isEmpty());
        // 스테이징에는 남은 주문의 몫(20)이 실물·예약으로 그대로 남는다
        assertEquals(20, staging.getOnHandQty());
        assertEquals(20, staging.getAlocQty());
    }

    @Test
    @DisplayName("스테이징에 예약이 모자라면 정합성 오류로 거부한다 — 조용히 실물만 빼지 않는다")
    void rejectsWhenStagingReservationIsShort() {
        pickedOrder(1L, 30);
        staging.release(10);   // 예약 20 < 출하 30

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> outbShmtService.confirm(request(1L)));

        assertTrue(e.getMessage().contains("정합성"));
        assertEquals(30, staging.getOnHandQty());
    }

    @Test
    @DisplayName("주문을 고르지 않으면 확정하지 않는다")
    void requiresOrders() {
        assertThrows(IllegalArgumentException.class, () -> outbShmtService.confirm(request()));
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────────

    /** 전량 집품된 PICKED 주문 — 스테이징에 실물·예약이 그만큼 쌓여 있다 */
    private OutbOrder pickedOrder(long id, long qty) {
        return pickedOrder(id, qty, qty);
    }

    /** 주문 {@code odrQty} 중 {@code pickedQty}만 집품된 PICKED 주문(나머지는 결품 종결로 닫힌 상태) */
    private OutbOrder pickedOrder(long id, long odrQty, long pickedQty) {
        OutbOrder order = order(id, odrQty);
        OutbLine line = order.getLines().get(0);
        Loc storage = mock(Loc.class);
        when(storage.getId()).thenReturn(id);
        Inv storageInv = Inv.builder().prod(prod).loc(storage).lot(lot).build();
        OutbAlloc alloc = OutbAlloc.builder().outbLine(line).inv(storageInv).alocQty(pickedQty).build();
        setId(alloc, id);
        alloc.addPikngQty(pickedQty);
        PikngTask task = PikngTask.builder()
                .wave(wave).outbAlloc(alloc).prod(prod).fromLoc(storage).lot(lot)
                .drctQty(pickedQty).srtSeq(1)
                .build();
        setId(task, id);
        task.execute(pickedQty);
        assertEquals(PikngTaskStatus.DONE, task.getStatus());
        tasks.add(task);
        // 피킹이 도착지에 남긴 것 — 실물과 예약이 함께
        staging.increaseOnHand(pickedQty);
        staging.reserve(pickedQty);
        order.recalcStatus(1, 0, 1);
        assertEquals(OutbStatus.PICKED, order.getStatus());
        return order;
    }

    /** 할당 0건인 CREATED 주문 — 지시취소 → 할당해제로 비워졌거나 한 번도 할당되지 못한 주문 */
    private OutbOrder createdOrder(long id, long qty) {
        return order(id, qty);
    }

    private OutbOrder order(long id, long qty) {
        OutbOrder order = OutbOrder.builder()
                .outbNo("OB-20260821-00" + id).omsOutbOrderId(++seq).store(mock(Store.class))
                .odrDe(LocalDate.of(2026, 8, 20)).expctDe(LocalDate.of(2026, 8, 21)).outbTyp("NRML")
                .build();
        setId(order, id);
        OutbLine line = OutbLine.builder().prod(prod).odrQty(qty).build();
        setId(line, id);
        order.addLine(line);
        order.assignWave(wave, WavRegTyp.MANUAL);
        waveOrders.add(order);
        return order;
    }

    private static ShmtConfirmRequest request(Long... ids) {
        ShmtConfirmRequest request = new ShmtConfirmRequest();
        request.setOutbOrderIds(List.of(ids));
        return request;
    }

    /** 엔티티 id는 DB가 채우므로 테스트에서는 리플렉션으로 넣는다 (기존 테스트들과 같은 방식) */
    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
