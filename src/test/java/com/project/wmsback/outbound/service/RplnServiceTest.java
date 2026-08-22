package com.project.wmsback.outbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.entity.InvMovTask;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvMovTaskRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.service.InvMovLockKey;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.outbound.dto.RplnActionResponse;
import com.project.wmsback.outbound.dto.RplnTaskRequest;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.outbound.repository.RplnQueryRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 수시보충 확정·취소. 재고 쓰기 포트는 실물이다 — 검증 대상이 「예약이 실물을 따라 도착지로 옮겨 가고
 * 할당이 그 행을 가리키게 되는가」이기 때문 (PikngServiceTest와 같은 방식).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RplnServiceTest {

    @Mock RplnQueryRepository rplnQueryRepository;
    @Mock InvMovTaskRepository invMovTaskRepository;
    @Mock PikngTaskRepository pikngTaskRepository;
    @Mock OutbWaveRepository outbWaveRepository;
    @Mock InvRepository invRepository;
    @Mock InvHistRepository invHistRepository;

    private RplnService rplnService;

    private Prod prod;
    private Lot lot;
    private Loc storage;
    private Loc picking;
    private Inv fromInv;
    private OutbAlloc alloc;
    private PikngTask pikngTask;
    private InvMovTask rpln;
    private final List<Inv> createdInvs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        rplnService = new RplnService(rplnQueryRepository, invMovTaskRepository, pikngTaskRepository,
                outbWaveRepository, new InvStore(invRepository, invHistRepository));
        createdInvs.clear();

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
        lot = mock(Lot.class);
        when(lot.getId()).thenReturn(7L);
        storage = mock(Loc.class);
        when(storage.getId()).thenReturn(5L);
        when(storage.getLocCd()).thenReturn("S-01");
        picking = mock(Loc.class);
        when(picking.getId()).thenReturn(2L);
        when(picking.getLocCd()).thenReturn("P-01");

        // 보관존 재고 100, 할당이 30 예약
        fromInv = Inv.builder().prod(prod).loc(storage).lot(lot).build();
        setId(fromInv, 50L);
        fromInv.increaseOnHand(100);
        fromInv.reserve(30);

        OutbLine line = OutbLine.builder().prod(prod).odrQty(30L).build();
        alloc = OutbAlloc.builder().outbLine(line).inv(fromInv).alocQty(30L).build();
        setId(alloc, 1L);

        OutbWave wave = OutbWave.builder().wavNo("WV-20260822-001").build();
        setId(wave, 100L);
        pikngTask = PikngTask.builder().wave(wave).outbAlloc(alloc).prod(prod)
                .fromLoc(picking).lot(lot).drctQty(30L).srtSeq(1).build();
        setId(pikngTask, 900L);

        rpln = InvMovTask.builder().invMovNo("MV-001").movDvsn(InvMovDvsn.RPLN)
                .prod(prod).lot(lot).fromLoc(storage).toLoc(picking).drctQty(30L).pikngTaskId(900L).build();
        setId(rpln, 10L);

        when(invMovTaskRepository.findLockKeysByIdIn(any()))
                .thenReturn(List.of(new InvMovLockKey(10L, 1L, 7L, 5L, 2L)));
        when(invMovTaskRepository.findPikngTaskIdsByIdIn(any())).thenReturn(List.of(900L));
        when(invMovTaskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(rpln));
        when(pikngTaskRepository.findWaveIdsByTaskIds(any())).thenReturn(List.of(100L));
        when(pikngTaskRepository.findById(900L)).thenReturn(Optional.of(pikngTask));
        when(outbWaveRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(wave));
        when(invRepository.findByKeyForUpdate(1L, 5L, 7L)).thenReturn(Optional.of(fromInv));
        when(invRepository.findByKeyForUpdate(1L, 2L, 7L)).thenReturn(Optional.empty());
        when(invRepository.findByProdIdAndLocIdAndLotId(1L, 2L, 7L)).thenReturn(Optional.empty());
        when(invRepository.save(any(Inv.class))).thenAnswer(i -> {
            Inv created = i.getArgument(0);
            createdInvs.add(created);
            return created;
        });
    }

    @Test
    @DisplayName("확정은 실물과 예약을 함께 피킹존으로 옮기고 할당이 도착지 행을 가리키게 한다 — 이력 RPLN 2행")
    void confirmMovesStockWithReservationAndRelocatesAlloc() {
        RplnActionResponse response = rplnService.confirm(request(10L));

        // 출발지: 실물 −30, 예약 −30
        assertEquals(70, fromInv.getOnHandQty());
        assertEquals(0, fromInv.getAlocQty());
        // 도착지: 실물 +30, 예약 +30 — 가용은 0 (보충분은 그 주문의 것)
        assertEquals(1, createdInvs.size());
        Inv toInv = createdInvs.get(0);
        assertEquals(30, toInv.getOnHandQty());
        assertEquals(30, toInv.getAlocQty());
        assertEquals(0, toInv.avalQty());
        // 할당이 따라간다
        assertSame(toInv, alloc.getInv());
        assertEquals(InvMovStatus.DONE, rpln.getStatus());
        assertEquals(30, rpln.getCmplQty());
        assertEquals(List.of("MV-001"), response.invMovNos());

        ArgumentCaptor<InvHist> hist = ArgumentCaptor.forClass(InvHist.class);
        verify(invHistRepository, org.mockito.Mockito.times(2)).save(hist.capture());
        assertTrue(hist.getAllValues().stream().allMatch(h -> h.getTxTyp() == TxTyp.RPLN));
    }

    @Test
    @DisplayName("락 순서 — 웨이브 → 재고 행 → 지시 행 (피킹 실행과 같은 순서)")
    void confirmLocksWaveThenInvThenTask() {
        rplnService.confirm(request(10L));

        InOrder order = inOrder(outbWaveRepository, invRepository, invMovTaskRepository);
        order.verify(outbWaveRepository).findByIdForUpdate(100L);
        order.verify(invRepository).findByKeyForUpdate(1L, 2L, 7L);
        order.verify(invMovTaskRepository).findByIdForUpdate(10L);
    }

    @Test
    @DisplayName("짝 피킹지시가 취소됐으면 확정할 수 없다 — 보충을 취소하는 길만 남긴다")
    void confirmRejectsWhenPairedTaskCancelled() {
        pikngTask.cancel();

        assertThrows(IllegalStateException.class, () -> rplnService.confirm(request(10L)));
        assertEquals(100, fromInv.getOnHandQty());
        assertEquals(30, fromInv.getAlocQty());
        assertEquals(InvMovStatus.DIRECTED, rpln.getStatus());
    }

    @Test
    @DisplayName("수시보충이 아닌 지시·지시 상태가 아닌 것은 이 경로가 받지 않는다")
    void confirmRejectsNonRplnOrNonDirected() {
        InvMovTask plain = InvMovTask.builder().invMovNo("MV-002").movDvsn(InvMovDvsn.INV_MOV)
                .prod(prod).lot(lot).fromLoc(storage).toLoc(picking).drctQty(30L).build();
        when(invMovTaskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(plain));
        assertThrows(IllegalArgumentException.class, () -> rplnService.confirm(request(10L)));

        rpln.cancelRemainder();
        when(invMovTaskRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(rpln));
        assertThrows(IllegalArgumentException.class, () -> rplnService.confirm(request(10L)));
    }

    @Test
    @DisplayName("취소는 예약을 건드리지 않는다 — 할당이 들고 있던 예약이 그대로 남는다")
    void cancelLeavesReservationUntouched() {
        RplnActionResponse response = rplnService.cancel(request(10L));

        assertEquals(InvMovStatus.CANCELLED, rpln.getStatus());
        assertEquals(100, fromInv.getOnHandQty());
        assertEquals(30, fromInv.getAlocQty());
        assertSame(fromInv, alloc.getInv());
        assertEquals(PikngTaskStatus.DIRECTED, pikngTask.getStatus());
        assertEquals(1, response.count());
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("보충지시를 고르지 않으면 거부한다")
    void requiresTask() {
        assertThrows(IllegalArgumentException.class, () -> rplnService.confirm(new RplnTaskRequest()));
    }

    private static RplnTaskRequest request(Long... ids) {
        RplnTaskRequest request = new RplnTaskRequest();
        request.setTaskIds(List.of(ids));
        return request;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
