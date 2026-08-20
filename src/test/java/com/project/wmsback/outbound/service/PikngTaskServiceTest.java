package com.project.wmsback.outbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.outbound.dto.PikngCancelRequest;
import com.project.wmsback.outbound.dto.PikngIssueRequest;
import com.project.wmsback.outbound.dto.PikngIssueResponse;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.entity.WavRegTyp;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.outbound.repository.PikngAcrstRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 피킹지시 발행·취소의 규칙. 저장소는 목으로 두고 <b>엔티티의 실제 상태 전이</b>
 * (웨이브 PLANNED ↔ ISSUED, 지시 CANCELLED)로 검증한다 — {@code OutbAllocServiceTest}와 같은 방식.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PikngTaskServiceTest {

    @Mock PikngTaskRepository pikngTaskRepository;
    @Mock PikngAcrstRepository pikngAcrstRepository;
    @Mock OutbAllocRepository outbAllocRepository;
    @Mock OutbOrderRepository outbOrderRepository;
    @Mock OutbWaveRepository outbWaveRepository;

    @InjectMocks PikngTaskService pikngTaskService;

    private OutbWave wave;
    private Prod prod;
    private Store store;
    private long seq;

    @BeforeEach
    void setUp() {
        seq = 0;
        wave = OutbWave.builder().wavNo("WV-20260820-001").build();
        setId(wave, 100L);

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        store = mock(Store.class);

        when(outbWaveRepository.findById(100L)).thenReturn(Optional.of(wave));
        when(outbWaveRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(wave));
        when(pikngTaskRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    // ── 발행 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("할당을 로케이션 순서(pikng_prty → loc_cd → 할당 id)로 정렬해 srt_seq를 부여한다")
    void issueAssignsSrtSeqByLocationOrder() {
        OutbOrder order = order("OB-001");
        OutbAlloc first = alloc(1L, order, 10, loc(2L, 1, "B-01"));   // 우선순위 1 → 첫 번째
        OutbAlloc second = alloc(2L, order, 20, loc(3L, 5, "A-01"));  // 우선순위 5 → 두 번째
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(order));
        when(outbAllocRepository.findAllWithDetailsByWaveId(100L)).thenReturn(List.of(second, first));

        PikngIssueResponse response = pikngTaskService.issue(issue(100L));

        ArgumentCaptor<List<PikngTask>> captor = ArgumentCaptor.captor();
        verify(pikngTaskRepository).saveAll(captor.capture());
        List<PikngTask> tasks = captor.getValue();
        assertEquals(2, tasks.size());
        assertEquals(first, tasks.get(0).getOutbAlloc());
        assertEquals(1, tasks.get(0).getSrtSeq());
        assertEquals(10, tasks.get(0).getDrctQty());
        assertEquals(second, tasks.get(1).getOutbAlloc());
        assertEquals(2, tasks.get(1).getSrtSeq());

        assertEquals(WaveStatus.ISSUED, wave.getStatus());
        assertNotNull(wave.getIssuedDt());
        assertEquals(2, response.taskCount());
    }

    @Test
    @DisplayName("할당이 0건인 주문이 섞여 있으면 웨이브째 발행을 거부한다 — 주문 단위 가드")
    void issueRejectsWaveWithNoAllocOrder() {
        OutbOrder allocated = order("OB-001");
        OutbOrder empty = order("OB-002");
        OutbAlloc alloc = alloc(1L, allocated, 10, loc(2L, 1, "B-01"));
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(allocated, empty));
        when(outbAllocRepository.findAllWithDetailsByWaveId(100L)).thenReturn(List.of(alloc));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> pikngTaskService.issue(issue(100L)));

        assertTrue(e.getMessage().contains("OB-002"));
        assertEquals(WaveStatus.PLANNED, wave.getStatus());
        verify(pikngTaskRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("이미 발행된 웨이브는 다시 발행할 수 없다")
    void issueRejectsIssuedWave() {
        wave.issue();
        assertThrows(IllegalStateException.class, () -> pikngTaskService.issue(issue(100L)));
    }

    @Test
    @DisplayName("웨이브를 고르지 않으면 발행하지 않는다")
    void issueRequiresWave() {
        assertThrows(IllegalArgumentException.class, () -> pikngTaskService.issue(issue()));
    }

    // ── 지시취소 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("실적 0이면 지시 전량이 CANCELLED로 남고 웨이브는 PLANNED로 복귀한다")
    void cancelRestoresWaveAndKeepsRows() {
        wave.issue();
        PikngTask task = task(alloc(1L, order("OB-001"), 10, loc(2L, 1, "B-01")), 10);
        when(pikngTaskRepository.findByWaveIdAndStatusNot(100L, PikngTaskStatus.CANCELLED))
                .thenReturn(List.of(task));

        pikngTaskService.cancel(cancel(100L));

        assertEquals(PikngTaskStatus.CANCELLED, task.getStatus());
        assertEquals(WaveStatus.PLANNED, wave.getStatus());
        assertNull(wave.getIssuedDt());
        verify(pikngTaskRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("피킹이 시작된 웨이브는 지시를 취소할 수 없다")
    void cancelRejectsPickedWave() {
        wave.issue();
        PikngTask task = task(alloc(1L, order("OB-001"), 10, loc(2L, 1, "B-01")), 10);
        task.execute(3);
        when(pikngTaskRepository.findByWaveIdAndStatusNot(100L, PikngTaskStatus.CANCELLED))
                .thenReturn(List.of(task));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> pikngTaskService.cancel(cancel(100L)));

        assertTrue(e.getMessage().contains("피킹이 시작된"));
        assertEquals(WaveStatus.ISSUED, wave.getStatus());
    }

    @Test
    @DisplayName("발행되지 않은 웨이브의 취소는 거부한다")
    void cancelRejectsPlannedWave() {
        assertThrows(IllegalStateException.class, () -> pikngTaskService.cancel(cancel(100L)));
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────────

    private OutbOrder order(String outbNo) {
        OutbOrder created = OutbOrder.builder()
                .outbNo(outbNo).omsOutbOrderId(++seq).store(store)
                .odrDe(LocalDate.of(2026, 8, 20)).expctDe(LocalDate.of(2026, 8, 21)).outbTyp("NRML")
                .build();
        setId(created, seq);
        created.assignWave(wave, WavRegTyp.MANUAL);
        return created;
    }

    private OutbAlloc alloc(long id, OutbOrder order, long qty, Loc loc) {
        OutbLine line = OutbLine.builder().prod(prod).odrQty(qty).build();
        setId(line, id);
        order.addLine(line);

        Lot lot = mock(Lot.class);
        Inv inv = Inv.builder().prod(prod).loc(loc).lot(lot).build();
        OutbAlloc created = OutbAlloc.builder().outbLine(line).inv(inv).alocQty(qty).build();
        setId(created, id);
        return created;
    }

    private PikngTask task(OutbAlloc alloc, long drctQty) {
        return PikngTask.builder()
                .wave(wave).outbAlloc(alloc)
                .prod(prod).fromLoc(alloc.getInv().getLoc()).lot(alloc.getInv().getLot())
                .drctQty(drctQty).srtSeq(1)
                .build();
    }

    private Loc loc(long id, int pikngPrty, String locCd) {
        Loc created = mock(Loc.class);
        when(created.getId()).thenReturn(id);
        when(created.getPikngPrty()).thenReturn(pikngPrty);
        when(created.getLocCd()).thenReturn(locCd);
        return created;
    }

    private PikngIssueRequest issue(Long... wavIds) {
        PikngIssueRequest request = new PikngIssueRequest();
        request.setWavIds(List.of(wavIds));
        return request;
    }

    private PikngCancelRequest cancel(Long... wavIds) {
        PikngCancelRequest request = new PikngCancelRequest();
        request.setWavIds(List.of(wavIds));
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
