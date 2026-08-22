package com.project.wmsback.outbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.service.InvLockKey;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.inventory.service.RsnValidator;
import com.project.mdm.code.entity.CodeDetailId;
import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.wmsback.outbound.dto.PikngCloseShortRequest;
import com.project.wmsback.outbound.dto.PikngCloseShortResponse;
import com.project.wmsback.outbound.dto.PikngExecuteRequest;
import com.project.wmsback.outbound.dto.PikngExecuteResponse;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.entity.WavRegTyp;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.outbound.repository.PikngAcrstRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LocRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.entity.InvMovTask;
import com.project.wmsback.inventory.repository.InvMovTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

/**
 * 피킹 실행의 규칙. 저장소는 목으로 두고 <b>재고 엔티티의 실제 상태</b>(실물·예약의 동시 소진,
 * 도착 스테이징 증가)와 <b>항등식 세 곳의 동시 갱신</b>(지시 cmpl · 할당 pikng · 실적 행)으로
 * 검증한다 — {@code OutbAllocServiceTest}와 같은 방식.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PikngServiceTest {

    @Mock PikngTaskRepository pikngTaskRepository;
    @Mock PikngAcrstRepository pikngAcrstRepository;
    @Mock OutbAllocRepository outbAllocRepository;
    @Mock OutbWaveRepository outbWaveRepository;
    @Mock LocRepository locRepository;
    @Mock InvRepository invRepository;
    @Mock InvHistRepository invHistRepository;
    @Mock CodeDetailRepository codeDetailRepository;
    @Mock InvMovTaskRepository invMovTaskRepository;

    // 재고 쓰기 포트는 목이 아니라 실물을 쓴다 — 예약 소진·실물 이동이 검증 대상이기 때문
    private PikngService pikngService;

    private OutbWave wave;
    private Prod prod;
    private Loc shipStage;
    private final Map<Long, Inv> invById = new HashMap<>();
    private final List<Inv> createdInvs = new ArrayList<>();
    private long seq;

    @BeforeEach
    void setUp() {
        // recalcStatus는 레포의 default 메서드 — mock이 비우지 않게 실제 몸체를 타게 한다
        doCallRealMethod().when(outbAllocRepository).recalcStatus(any());
        pikngService = new PikngService(pikngTaskRepository, pikngAcrstRepository, outbAllocRepository,
                outbWaveRepository, locRepository, new InvStore(invRepository, invHistRepository),
                new RsnValidator(codeDetailRepository), invMovTaskRepository);

        invById.clear();
        createdInvs.clear();
        seq = 0;

        // 주문 상태 재산출의 재료 — 이 테스트들은 전부 피킹이 일어난 뒤를 본다(실적 있는 할당 1건).
        // 소진 여부(countUnpickedByOrderId)만 각 테스트가 덮어 PICKING/PICKED를 가른다
        when(outbAllocRepository.countByOutbOrderId(anyLong())).thenReturn(1L);
        when(outbAllocRepository.countPickedByOrderId(anyLong())).thenReturn(1L);

        wave = OutbWave.builder().wavNo("WV-20260820-001").build();
        setId(wave, 100L);
        wave.issue();

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
        when(prod.getProdCd()).thenReturn("PROD-0001");

        shipStage = mock(Loc.class);
        when(shipStage.getId()).thenReturn(900L);
        when(shipStage.getLocCd()).thenReturn("SHIP-STAGE");

        when(outbWaveRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(wave));
        when(pikngTaskRepository.findWaveIdsByTaskIds(any())).thenReturn(List.of(100L));
        when(locRepository.findByLocCd("SHIP-STAGE")).thenReturn(Optional.of(shipStage));
        when(pikngAcrstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // 도착지(SHIP-STAGE) 재고는 없으면 만들어진다 — findOrCreate의 save 경로
        when(invRepository.save(any(Inv.class))).thenAnswer(i -> {
            Inv created = i.getArgument(0);
            createdInvs.add(created);
            return created;
        });
        // 락 경로(InvStore.lockAllByIds): id → 키 선조회 → 키 락. 픽스처 저장소 invById로 흉내낸다
        when(invRepository.findLockKeysByIdIn(any())).thenAnswer(i -> {
            List<InvLockKey> rows = new ArrayList<>();
            for (Long id : i.<Collection<Long>>getArgument(0)) {
                Inv found = invById.get(id);
                if (found != null) {
                    rows.add(new InvLockKey(id, found.getProd().getId(), found.getLoc().getId(), found.getLot().getId()));
                }
            }
            return rows;
        });
        when(codeDetailRepository.existsById(any(CodeDetailId.class))).thenReturn(true);
        when(invRepository.findByKeyForUpdate(any(), any(), any()))
                .thenAnswer(i -> invById.values().stream()
                        .filter(candidate -> candidate.getProd().getId().equals(i.getArgument(0))
                                && candidate.getLoc().getId().equals(i.getArgument(1))
                                && candidate.getLot().getId().equals(i.getArgument(2)))
                        .findFirst());
    }

    @Test
    @DisplayName("짝 보충지시가 확정되기 전에는 집을 수 없다 — 실물이 아직 보관존에 있고 지시의 from에는 없다")
    void executeRejectsBeforeReplenishmentDone() {
        PikngTask task = task(1L, 30, 100);
        InvMovTask rpln = mock(InvMovTask.class);
        when(rpln.getPikngTaskId()).thenReturn(1L);
        when(rpln.getStatus()).thenReturn(InvMovStatus.DIRECTED);
        when(rpln.getInvMovNo()).thenReturn("MV-001");
        when(invMovTaskRepository.findByPikngTaskIdIn(any())).thenReturn(List.of(rpln));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> pikngService.execute(execute(item(1L, 30L))));
        assertTrue(e.getMessage().contains("보충"));
        assertEquals(100, task.getOutbAlloc().getInv().getOnHandQty());
        assertEquals(0, task.getCmplQty());

        // 보충이 끝나면 집는다
        when(rpln.getStatus()).thenReturn(InvMovStatus.DONE);
        when(outbAllocRepository.countUnpickedByOrderId(anyLong())).thenReturn(0L);
        pikngService.execute(execute(item(1L, 30L)));
        assertEquals(30, task.getCmplQty());
    }

    @Test
    @DisplayName("짝 보충이 취소된 지시도 집을 수 없다 — 취소된 짝을 빼고 읽으면 「짝이 없는 지시」로 통과한다")
    void executeRejectsWhenPairedReplenishmentCancelled() {
        PikngTask task = task(1L, 30, 100);
        InvMovTask rpln = mock(InvMovTask.class);
        when(rpln.getPikngTaskId()).thenReturn(1L);
        when(rpln.getStatus()).thenReturn(InvMovStatus.CANCELLED);
        when(rpln.getInvMovNo()).thenReturn("MV-001");
        when(invMovTaskRepository.findByPikngTaskIdIn(any())).thenReturn(List.of(rpln));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> pikngService.execute(execute(item(1L, 30L))));
        assertTrue(e.getMessage().contains("보충"));
        assertEquals(100, task.getOutbAlloc().getInv().getOnHandQty());
        assertEquals(0, task.getCmplQty());
    }

    @Test
    @DisplayName("실물이 예약보다 적으면 읽을 수 있는 메시지로 막는다 — DB CHECK 위반을 원문으로 노출하지 않는다")
    void executeRejectsWhenStockBelowRequestedQty() {
        // 장부가 어긋난 상태 — 예약 30인데 실물은 10뿐이다
        PikngTask task = task(1L, 30, 10);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> pikngService.execute(execute(item(1L, 30L))));
        assertTrue(e.getMessage().contains("실물"));
        assertEquals(10, task.getOutbAlloc().getInv().getOnHandQty());
        assertEquals(0, task.getCmplQty());
    }

    @Test
    @DisplayName("피킹은 출발지의 실물·예약을 함께 소진하고 SHIP-STAGE에 실물·예약을 함께 쌓는다 — 지시·할당·실적 세 곳 동시 갱신")
    void executeMovesStockAndKeepsIdentity() {
        PikngTask task = task(1L, 30, 100);
        Inv storage = task.getOutbAlloc().getInv();
        when(outbAllocRepository.countUnpickedByOrderId(anyLong())).thenReturn(0L);

        PikngExecuteResponse response = pikngService.execute(execute(item(1L, 30L)));

        // 출발지: 실물 −30, 예약 −30 (aloc ≤ on_hand 불변식 유지)
        assertEquals(70, storage.getOnHandQty());
        assertEquals(0, storage.getAlocQty());
        // 도착지(SHIP-STAGE): 실물 +30, 예약 +30 — 예약이 실물을 따라간다 (스테이징 가용 0)
        assertEquals(1, createdInvs.size());
        assertEquals(30, createdInvs.get(0).getOnHandQty());
        assertEquals(30, createdInvs.get(0).getAlocQty());
        assertEquals(0, createdInvs.get(0).avalQty());
        // 항등식 — 지시 cmpl = 할당 pikng = 실적 합
        assertEquals(30, task.getCmplQty());
        assertEquals(PikngTaskStatus.DONE, task.getStatus());
        assertEquals(30, task.getOutbAlloc().getPikngQty());
        verify(pikngAcrstRepository).save(any());
        // 이력 PICK 2행 (출발 −, 도착 +)
        ArgumentCaptor<InvHist> hist = ArgumentCaptor.forClass(InvHist.class);
        verify(invHistRepository, atLeastOnce()).save(hist.capture());
        assertEquals(2, hist.getAllValues().size());
        assertTrue(hist.getAllValues().stream().allMatch(h -> h.getTxTyp() == TxTyp.PICK));
        // 전 할당 소진 → PICKED
        assertEquals(OutbStatus.PICKED, task.getOutbAlloc().getOutbLine().getOutbOrder().getStatus());
        assertEquals(1, response.doneTaskCount());
        assertEquals(1, response.orderChanges().size());
    }

    @Test
    @DisplayName("부분 피킹 — 지시는 DIRECTED로 남고 주문은 PICKING이 된다")
    void partialPickingKeepsDirected() {
        PikngTask task = task(1L, 30, 100);
        when(outbAllocRepository.countUnpickedByOrderId(anyLong())).thenReturn(1L);

        pikngService.execute(execute(item(1L, 10L)));

        assertEquals(PikngTaskStatus.DIRECTED, task.getStatus());
        assertEquals(20, task.remainingQty());
        assertEquals(10, task.getOutbAlloc().getPikngQty());
        assertEquals(90, task.getOutbAlloc().getInv().getOnHandQty());
        assertEquals(20, task.getOutbAlloc().getInv().getAlocQty());
        assertEquals(OutbStatus.PICKING, task.getOutbAlloc().getOutbLine().getOutbOrder().getStatus());
    }

    @Test
    @DisplayName("지시 잔량을 초과한 요청은 거부한다 — 재고는 움직이지 않는다")
    void rejectsOverRemaining() {
        PikngTask task = task(1L, 30, 100);
        task.execute(25);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> pikngService.execute(execute(item(1L, 10L))));

        assertTrue(e.getMessage().contains("잔량"));
        assertEquals(100, task.getOutbAlloc().getInv().getOnHandQty());
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("취소된 지시는 실행할 수 없다")
    void rejectsCancelledTask() {
        PikngTask task = task(1L, 30, 100);
        task.cancel();

        assertThrows(IllegalArgumentException.class,
                () -> pikngService.execute(execute(item(1L, 10L))));
    }

    @Test
    @DisplayName("같은 지시를 중복 지정하면 거부한다")
    void rejectsDuplicateItems() {
        task(1L, 30, 100);
        assertThrows(IllegalArgumentException.class,
                () -> pikngService.execute(execute(item(1L, 5L), item(1L, 5L))));
    }

    @Test
    @DisplayName("수량 0 이하는 거부한다")
    void rejectsNonPositiveQty() {
        assertThrows(IllegalArgumentException.class,
                () -> pikngService.execute(execute(item(1L, 0L))));
    }

    // ── 결품 종결 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("결품 종결 — 잔량만큼 예약을 풀고 지시·할당을 실적까지 낮춰 닫는다")
    void closeShortReleasesReservationAndClosesTask() {
        PikngTask task = task(1L, 30, 100);
        // 실행 시엔 잔량 5가 남아 주문이 PICKING, 종결 뒤엔 소진돼 PICKED가 된다
        when(outbAllocRepository.countUnpickedByOrderId(anyLong())).thenReturn(1L, 0L);
        pikngService.execute(execute(item(1L, 25L)));
        Inv storage = task.getOutbAlloc().getInv();
        // 25만 나갔고 실물 없는 5가 예약으로 남아 있다 — 이 상태가 곧 교착이었다
        assertEquals(75, storage.getOnHandQty());
        assertEquals(5, storage.getAlocQty());
        assertEquals(OutbStatus.PICKING, task.getOutbAlloc().getOutbLine().getOutbOrder().getStatus());

        PikngCloseShortResponse response = pikngService.closeShort(closeShort(shortItem(1L, "NOSTOCK", null)));

        // 예약만 풀린다 — 실물(on_hand)은 건드리지 않는다 (장부를 줄이는 경로는 재고조사뿐)
        assertEquals(75, storage.getOnHandQty());
        assertEquals(0, storage.getAlocQty());
        // 지시·할당이 실적까지 내려와 항등식(drct = aloc, cmpl = pikng)이 유지된다
        assertEquals(25, task.getDrctQty());
        assertEquals(25, task.getCmplQty());
        assertEquals(25, task.getOutbAlloc().getAlocQty());
        assertEquals(25, task.getOutbAlloc().getPikngQty());
        assertEquals(PikngTaskStatus.DONE, task.getStatus());
        // 결품수량·사유는 종결 후 파생이 불가능해 컬럼으로 남는다
        assertEquals(5, task.getShotgeQty());
        assertEquals("NOSTOCK", task.getShotgeRsnCd());
        assertEquals(5, response.shotgeQty());
        // 남은 할당이 소진돼 주문이 PICKED로 닫힌다
        assertEquals(OutbStatus.PICKED, task.getOutbAlloc().getOutbLine().getOutbOrder().getStatus());
        assertEquals(1, response.orderChanges().size());
    }

    @Test
    @DisplayName("실적이 없는 지시는 결품 종결이 아니라 지시취소 대상이다")
    void closeShortRejectsUntouchedTask() {
        PikngTask task = task(1L, 30, 100);

        assertThrows(IllegalArgumentException.class,
                () -> pikngService.closeShort(closeShort(shortItem(1L, "NOSTOCK", null))));

        assertEquals(30, task.getOutbAlloc().getInv().getAlocQty());
    }

    @Test
    @DisplayName("결품사유 없이는 종결할 수 없다 — 잔량을 없앤 근거가 남지 않는다")
    void closeShortRequiresReason() {
        PikngTask task = task(1L, 30, 100);
        when(outbAllocRepository.countUnpickedByOrderId(anyLong())).thenReturn(1L);
        pikngService.execute(execute(item(1L, 25L)));

        assertThrows(IllegalArgumentException.class,
                () -> pikngService.closeShort(closeShort(shortItem(1L, null, null))));

        assertEquals(5, task.getOutbAlloc().getInv().getAlocQty());
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────────

    /** 지시 + 할당 + 보관 재고 한 벌. 재고는 지시수량만큼 예약된 상태로 만든다 */
    private PikngTask task(long id, long drctQty, long onHand) {
        Store store = mock(Store.class);
        OutbOrder order = OutbOrder.builder()
                .outbNo("OB-20260820-00" + id).omsOutbOrderId(++seq).store(store)
                .odrDe(LocalDate.of(2026, 8, 20)).expctDe(LocalDate.of(2026, 8, 21)).outbTyp("NRML")
                .build();
        setId(order, id);
        OutbLine line = OutbLine.builder().prod(prod).odrQty(drctQty).build();
        setId(line, id);
        order.addLine(line);
        order.assignWave(wave, WavRegTyp.MANUAL);
        order.recalcStatus(1, 1, 0);

        Lot lot = mock(Lot.class);
        when(lot.getId()).thenReturn(id);
        Loc loc = mock(Loc.class);
        when(loc.getId()).thenReturn(id);
        when(loc.getLocCd()).thenReturn("A-01-" + id);

        Inv inv = Inv.builder().prod(prod).loc(loc).lot(lot).build();
        setId(inv, id);
        inv.increaseOnHand(onHand);
        inv.reserve(drctQty);
        invById.put(id, inv);

        OutbAlloc alloc = OutbAlloc.builder().outbLine(line).inv(inv).alocQty(drctQty).build();
        setId(alloc, id);

        PikngTask created = PikngTask.builder()
                .wave(wave).outbAlloc(alloc).prod(prod).fromLoc(loc).lot(lot)
                .drctQty(drctQty).srtSeq(1)
                .build();
        setId(created, id);
        when(pikngTaskRepository.findAllWithDetailsByIds(any())).thenReturn(List.of(created));
        return created;
    }

    private PikngExecuteRequest execute(PikngExecuteRequest.Item... items) {
        PikngExecuteRequest request = new PikngExecuteRequest();
        request.setItems(List.of(items));
        return request;
    }

    private PikngExecuteRequest.Item item(long taskId, Long qty) {
        PikngExecuteRequest.Item created = new PikngExecuteRequest.Item();
        created.setPikngTaskId(taskId);
        created.setQty(qty);
        return created;
    }

    private PikngCloseShortRequest closeShort(PikngCloseShortRequest.Item... items) {
        PikngCloseShortRequest request = new PikngCloseShortRequest();
        request.setItems(List.of(items));
        return request;
    }

    private PikngCloseShortRequest.Item shortItem(long taskId, String rsnCd, String rsnDscr) {
        PikngCloseShortRequest.Item created = new PikngCloseShortRequest.Item();
        created.setPikngTaskId(taskId);
        created.setRsnCd(rsnCd);
        created.setRsnDscr(rsnDscr);
        return created;
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
