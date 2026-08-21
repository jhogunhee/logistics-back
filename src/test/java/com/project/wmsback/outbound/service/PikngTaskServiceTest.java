package com.project.wmsback.outbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.outbound.dto.PikngCancelRequest;
import com.project.wmsback.outbound.dto.PikngCancelResponse;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
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
        when(pikngTaskRepository.findWaveIdsByTaskIds(anyCollection())).thenReturn(List.of(100L));
    }

    // ── 발행 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("할당을 로케이션 순서(pikng_prty → loc_cd → 할당 id)로 정렬해 srt_seq를 부여한다")
    void issueAssignsSrtSeqByLocationOrder() {
        OutbOrder order = order("OB-001");
        OutbAlloc first = alloc(1L, order, 10, loc(2L, 1, "B-01"));   // 우선순위 1 → 첫 번째
        OutbAlloc second = alloc(2L, order, 20, loc(3L, 5, "A-01"));  // 우선순위 5 → 두 번째
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(order));
        when(outbAllocRepository.findIssuableByWaveId(100L, PikngTaskStatus.CANCELLED)).thenReturn(List.of(second, first));
        when(outbAllocRepository.findAllocatedOrderIdsByWaveId(100L)).thenReturn(List.of(order.getId()));

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
        when(outbAllocRepository.findIssuableByWaveId(100L, PikngTaskStatus.CANCELLED)).thenReturn(List.of(alloc));
        when(outbAllocRepository.findAllocatedOrderIdsByWaveId(100L)).thenReturn(List.of(allocated.getId()));

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
    @DisplayName("피킹이 시작된 웨이브는 발행을 통째로 취소할 수 없다 — 지시 단위로 유도한다")
    void cancelRejectsPickedWave() {
        wave.issue();
        PikngTask task = task(alloc(1L, order("OB-001"), 10, loc(2L, 1, "B-01")), 10);
        task.execute(3);
        when(pikngTaskRepository.findByWaveIdAndStatusNot(100L, PikngTaskStatus.CANCELLED))
                .thenReturn(List.of(task));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> pikngTaskService.cancel(cancel(100L)));

        assertTrue(e.getMessage().contains("피킹이 시작된"));
        assertTrue(e.getMessage().contains("골라 취소"));
        assertEquals(WaveStatus.ISSUED, wave.getStatus());
    }

    @Test
    @DisplayName("발행되지 않은 웨이브의 취소는 거부한다")
    void cancelRejectsPlannedWave() {
        assertThrows(IllegalStateException.class, () -> pikngTaskService.cancel(cancel(100L)));
    }

    // ── 추가 발행 ────────────────────────────────────────────────────────────

    /**
     * 결품 종결이 잔량을 사후에 키우거나 재할당이 들어오면 발행된 웨이브에 「지시 없는 할당」이
     * 생긴다. 그것을 현장에 내보내는 유일한 문이 추가 발행이고, 순번은 <b>기존 뒤에</b> 붙는다 —
     * 1차 동선을 다 돈 뒤의 추가분이라 현장과 맞는다.
     */
    @Test
    @DisplayName("추가 발행은 집품 순번을 기존 뒤에 이어붙이고 웨이브 상태는 건드리지 않는다")
    void issueAdditionalAppendsAfterExistingSeq() {
        wave.issue();
        LocalDateTime issuedAt = wave.getIssuedDt();
        OutbOrder order = order("OB-001");
        OutbAlloc added = alloc(3L, order, 15, loc(4L, 2, "C-01"));
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(order));
        when(outbAllocRepository.findIssuableByWaveId(100L, PikngTaskStatus.CANCELLED)).thenReturn(List.of(added));
        when(outbAllocRepository.findAllocatedOrderIdsByWaveId(100L)).thenReturn(List.of(order.getId()));
        when(pikngTaskRepository.findMaxSrtSeqByWaveId(100L)).thenReturn(7);

        PikngIssueResponse response = pikngTaskService.issueAdditional(issue(100L));

        ArgumentCaptor<List<PikngTask>> captor = ArgumentCaptor.captor();
        verify(pikngTaskRepository).saveAll(captor.capture());
        assertEquals(8, captor.getValue().get(0).getSrtSeq());
        assertEquals(1, response.taskCount());
        assertEquals(WaveStatus.ISSUED, wave.getStatus());
        assertEquals(issuedAt, wave.getIssuedDt());   // 최초 발행 시각을 덮어쓰지 않는다
    }

    @Test
    @DisplayName("할당 0건 주문이 남아 있으면 추가 발행도 거부한다 — 입구 가드가 출구에도 선다")
    void issueAdditionalRejectsWaveWithNoAllocOrder() {
        wave.issue();
        OutbOrder allocated = order("OB-001");
        OutbOrder trapped = order("OB-002");
        OutbAlloc added = alloc(3L, allocated, 15, loc(4L, 2, "C-01"));
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(allocated, trapped));
        when(outbAllocRepository.findIssuableByWaveId(100L, PikngTaskStatus.CANCELLED)).thenReturn(List.of(added));
        when(outbAllocRepository.findAllocatedOrderIdsByWaveId(100L)).thenReturn(List.of(allocated.getId()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> pikngTaskService.issueAdditional(issue(100L)));

        assertTrue(e.getMessage().contains("OB-002"));
        verify(pikngTaskRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("나갈 할당이 없으면 추가 발행을 거부한다")
    void issueAdditionalRejectsWhenNothingPending() {
        wave.issue();
        OutbOrder order = order("OB-001");
        when(outbOrderRepository.findByWaveId(100L)).thenReturn(List.of(order));
        when(outbAllocRepository.findIssuableByWaveId(100L, PikngTaskStatus.CANCELLED)).thenReturn(List.of());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> pikngTaskService.issueAdditional(issue(100L)));

        assertTrue(e.getMessage().contains("추가로 발행할 할당이 없습니다"));
    }

    @Test
    @DisplayName("두 진입은 서로의 자리를 침범하지 않는다 — 최초는 PLANNED만, 추가는 ISSUED만")
    void issueEntriesGuardEachOther() {
        assertThrows(IllegalStateException.class, () -> pikngTaskService.issueAdditional(issue(100L)));

        wave.issue();
        assertThrows(IllegalStateException.class, () -> pikngTaskService.issue(issue(100L)));
    }

    // ── 지시 단위 취소 ────────────────────────────────────────────────────────

    /**
     * 실적이 섞인 웨이브에서 실적 0인 지시가 닫히는지 — 지시 단위 취소가 없으면
     * 이 지시는 웨이브 단위 취소(다른 지시의 실적에 막힘)에도, 결품 종결(실적 0이라 안 열림)에도
     * 걸리지 않아 그 예약이 영구히 묶인다.
     */
    @Test
    @DisplayName("같은 웨이브에 실적이 있어도 실적 0인 지시는 단독으로 취소된다")
    void cancelTaskIsNotHostageToAnotherTasksPicking() {
        wave.issue();
        PikngTask picked = task(alloc(1L, order("OB-001"), 10, loc(2L, 1, "B-01")), 10);
        picked.execute(10);
        PikngTask untouched = task(alloc(2L, order("OB-002"), 30, loc(3L, 2, "B-02")), 30);
        setId(untouched, 900L);
        when(pikngTaskRepository.findAllWithDetailsByIds(List.of(900L))).thenReturn(List.of(untouched));
        when(pikngTaskRepository.findByWaveIdAndStatusNot(100L, PikngTaskStatus.CANCELLED))
                .thenReturn(List.of(picked));   // 취소 후 남는 살아 있는 지시 = 집힌 쪽

        PikngCancelResponse response = pikngTaskService.cancel(cancelTasks(900L));

        assertEquals(PikngTaskStatus.CANCELLED, untouched.getStatus());
        assertEquals(PikngTaskStatus.DONE, picked.getStatus());
        assertEquals(1, response.cancelledCount());
        // 살아 있는 지시가 남았으므로 웨이브는 그대로 ISSUED다
        assertEquals(WaveStatus.ISSUED, wave.getStatus());
    }

    @Test
    @DisplayName("취소로 살아 있는 지시가 하나도 남지 않으면 웨이브도 PLANNED로 돌아간다")
    void cancelTaskRestoresWaveWhenNoLiveTaskRemains() {
        wave.issue();
        PikngTask only = task(alloc(1L, order("OB-001"), 10, loc(2L, 1, "B-01")), 10);
        setId(only, 900L);
        when(pikngTaskRepository.findAllWithDetailsByIds(List.of(900L))).thenReturn(List.of(only));
        when(pikngTaskRepository.findByWaveIdAndStatusNot(100L, PikngTaskStatus.CANCELLED))
                .thenReturn(List.of());

        pikngTaskService.cancel(cancelTasks(900L));

        assertEquals(PikngTaskStatus.CANCELLED, only.getStatus());
        assertEquals(WaveStatus.PLANNED, wave.getStatus());
        assertNull(wave.getIssuedDt());
    }

    @Test
    @DisplayName("실적이 있는 지시는 지시 단위로도 취소할 수 없다 — 결품 종결의 몫이다")
    void cancelTaskRejectsPickedTask() {
        wave.issue();
        PikngTask partial = task(alloc(1L, order("OB-001"), 10, loc(2L, 1, "B-01")), 10);
        partial.execute(4);
        setId(partial, 900L);
        when(pikngTaskRepository.findAllWithDetailsByIds(List.of(900L))).thenReturn(List.of(partial));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> pikngTaskService.cancel(cancelTasks(900L)));

        assertTrue(e.getMessage().contains("이미 피킹된 수량"));
        assertEquals(WaveStatus.ISSUED, wave.getStatus());
    }

    @Test
    @DisplayName("웨이브와 지시를 함께 지정하거나 둘 다 비우면 거부한다 — 취소 단위는 하나다")
    void cancelRequiresExactlyOneScope() {
        PikngCancelRequest both = new PikngCancelRequest();
        both.setWavIds(List.of(100L));
        both.setTaskIds(List.of(900L));
        assertThrows(IllegalArgumentException.class, () -> pikngTaskService.cancel(both));
        assertThrows(IllegalArgumentException.class,
                () -> pikngTaskService.cancel(new PikngCancelRequest()));
    }

    /**
     * 락을 먼저 잡고 지시를 나중에 읽어야 한다. 뒤집히면 락을 기다리는 동안 커밋된 피킹 실행이
     * 영속성 컨텍스트에 반영되지 않아, 낡은 {@code cmplQty 0}이 취소 가드를 통과하고 flush가
     * 그 0을 되써서 항등식(cmpl_qty = pikng_qty = SUM(acrst))이 조용히 깨진다.
     */
    @Test
    @DisplayName("지시를 읽기 전에 웨이브 락을 잡는다 — 실행·결품 종결과 같은 순서")
    void cancelTaskLocksWaveBeforeReadingTasks() {
        wave.issue();
        PikngTask only = task(alloc(1L, order("OB-001"), 10, loc(2L, 1, "B-01")), 10);
        setId(only, 900L);
        when(pikngTaskRepository.findAllWithDetailsByIds(List.of(900L))).thenReturn(List.of(only));
        when(pikngTaskRepository.findByWaveIdAndStatusNot(100L, PikngTaskStatus.CANCELLED))
                .thenReturn(List.of());

        pikngTaskService.cancel(cancelTasks(900L));

        InOrder order = inOrder(pikngTaskRepository, outbWaveRepository);
        order.verify(pikngTaskRepository).findWaveIdsByTaskIds(List.of(900L));
        order.verify(outbWaveRepository).findByIdForUpdate(100L);
        order.verify(pikngTaskRepository).findAllWithDetailsByIds(List.of(900L));
    }

    @Test
    @DisplayName("존재하지 않는 지시가 섞이면 전량 거부한다")
    void cancelTaskRejectsUnknownTask() {
        wave.issue();
        when(pikngTaskRepository.findAllWithDetailsByIds(List.of(900L))).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> pikngTaskService.cancel(cancelTasks(900L)));
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

    private PikngCancelRequest cancelTasks(Long... taskIds) {
        PikngCancelRequest request = new PikngCancelRequest();
        request.setTaskIds(List.of(taskIds));
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
