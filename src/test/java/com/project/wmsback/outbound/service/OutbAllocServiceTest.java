package com.project.wmsback.outbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.store.entity.Store;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.service.InvLockKey;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.outbound.dto.AllocExecuteRequest;
import com.project.wmsback.outbound.dto.AllocExecuteResponse;
import com.project.wmsback.outbound.dto.AllocReleaseRequest;
import com.project.wmsback.outbound.dto.ManualAllocRequest;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WavRegTyp;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbLineRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.strategy.allocation.repository.AllocQueryRepository;
import com.project.wmsback.strategy.allocation.service.AlocStgyService;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 할당의 계산 규칙. 저장소는 목으로 두고 <b>재고 엔티티의 실제 상태</b>(예약 증감·가용 잔량)로 검증한다 —
 * {@code InvMovServiceTest}가 이동지시 예약을 검증하는 것과 같은 방식이다.
 *
 * <p>여기서 못 덮는 것은 동시성 하나뿐이다(락 순서·데드락). 통합 테스트를 두지 않기로 해서
 * 그 규칙은 {@code InvStore}의 락 창구(키 오름차순)와 코드 리뷰로 지켜진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutbAllocServiceTest {

    @Mock OutbAllocRepository outbAllocRepository;
    @Mock OutbWaveRepository outbWaveRepository;
    @Mock OutbLineRepository outbLineRepository;
    @Mock InvRepository invRepository;
    @Mock InvHistRepository invHistRepository; // 예약·해제는 이력을 남기지 않는다 — InvStore 생성에만 필요
    // 이 테스트는 전부 「전략 미설정」 상태를 본다 — 산정기의 기본 동작(FEFO · 점포 잔여수명 ·
    // 순차 소진)이 전략 도입 전과 같은지가 여기 검증의 전제다. 전략별 동작은 산정기 테스트 몫.
    @Mock AlocStgyService alocStgyService;
    @Mock AllocQueryRepository allocQueryRepository;
    @Mock StgyExecLogService stgyExecLogService;

    // 재고 쓰기 포트는 목이 아니라 실물을 쓴다 — 예약(aloc) 증감이 검증 대상이기 때문
    private OutbAllocService outbAllocService;

    private static final LocalDate EXPCT_DE = LocalDate.of(2026, 8, 10);

    private OutbWave wave;
    private Prod prod;
    private Store store;
    private final Map<Long, Inv> invById = new HashMap<>();
    private long seq;

    @BeforeEach
    void setUp() {
        outbAllocService = new OutbAllocService(outbAllocRepository, outbWaveRepository, outbLineRepository,
                new InvStore(invRepository, invHistRepository),
                alocStgyService, allocQueryRepository, stgyExecLogService);

        invById.clear();
        seq = 0;

        wave = OutbWave.builder().wavNo("WV-20260803-001").build();
        setId(wave, 100L);

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
        when(prod.getProdCd()).thenReturn("PROD-0001");

        store = mock(Store.class);
        when(store.getOutbLifeRate()).thenReturn((short) 50);

        when(outbWaveRepository.findById(100L)).thenReturn(Optional.of(wave));
        when(outbAllocRepository.sumAlocQtyByLineIds(anyList())).thenReturn(Map.of());
        when(outbAllocRepository.findByOutbLineIdIn(anyList())).thenReturn(List.of());
        when(outbAllocRepository.save(any(OutbAlloc.class))).thenAnswer(i -> i.getArgument(0));
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
        when(invRepository.findByKeyForUpdate(any(), any(), any()))
                .thenAnswer(i -> invById.values().stream()
                        .filter(candidate -> candidate.getProd().getId().equals(i.getArgument(0))
                                && candidate.getLoc().getId().equals(i.getArgument(1))
                                && candidate.getLot().getId().equals(i.getArgument(2)))
                        .findFirst());
        when(alocStgyService.select(anyList())).thenReturn(Optional.empty());
        when(allocQueryRepository.bizDvsnByZon()).thenReturn(Map.of());
    }

    // ── 자동할당 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FEFO — 유통기한이 임박한 Lot부터 소진한다")
    void allocatesFefoFirst() {
        Inv fresh = inv(1L, 100, LocalDate.of(2026, 12, 31));
        Inv near = inv(2L, 100, LocalDate.of(2026, 9, 30));
        candidates(near, fresh);          // 저장소가 FEFO 순으로 돌려준다
        OutbLine line = line(1L, 30);
        targetLines(line);

        AllocExecuteResponse result = execute();

        assertEquals(30, result.alocQty());
        assertEquals(30, near.getAlocQty());
        assertEquals(0, fresh.getAlocQty());
    }

    @Test
    @DisplayName("재고가 모자라면 있는 만큼만 채우고 잔량을 남긴다 — 부분할당, 백오더 없음")
    void partialAllocation() {
        Inv only = inv(1L, 10, LocalDate.of(2026, 12, 31));
        candidates(only);
        OutbLine line = line(1L, 25);
        targetLines(line);

        AllocExecuteResponse result = execute();

        assertEquals(25, result.reqQty());
        assertEquals(10, result.alocQty());
        assertEquals(15, result.shortQty());
        assertEquals(OutbStatus.ALLOCATED, line.getOutbOrder().getStatus());
    }

    @Test
    @DisplayName("재고가 부족하면 앞선 라인이 다 가져간다 — 순차 소진, 균등분배 없음")
    void sequentialConsumptionNotEvenSplit() {
        Inv only = inv(1L, 10, LocalDate.of(2026, 12, 31));
        candidates(only);
        OutbLine first = line(1L, 10, "OB-20260803-001");
        OutbLine second = line(2L, 10, "OB-20260803-002");
        targetLines(first, second);

        AllocExecuteResponse result = execute();

        assertEquals(10, result.alocQty());
        assertEquals(10, lineResult(result, 1L).alocQty());
        assertEquals(0, lineResult(result, 2L).alocQty());
        assertEquals(10, lineResult(result, 2L).shortQty());
    }

    @Test
    @DisplayName("같은 상품 라인이 둘이어도 후보 리스트 하나를 나눠 쓴다 — 이중 배분 없음")
    void sharesCandidateListAcrossLines() {
        Inv only = inv(1L, 30, LocalDate.of(2026, 12, 31));
        candidates(only);
        targetLines(line(1L, 20, "OB-20260803-001"), line(2L, 20, "OB-20260803-002"));

        AllocExecuteResponse result = execute();

        assertEquals(30, result.alocQty());
        assertEquals(30, only.getAlocQty());
        assertEquals(0, only.avalQty());
    }

    @Test
    @DisplayName("가용재고만 본다 — 예약분·보류분은 후보 수량에서 빠진다")
    void usesAvailableQtyOnly() {
        Inv partly = inv(1L, 100, LocalDate.of(2026, 12, 31));
        partly.reserve(70);
        partly.hold(20);                  // 가용 = 100 − 70 − 20 = 10
        candidates(partly);
        targetLines(line(1L, 50));

        AllocExecuteResponse result = execute();

        assertEquals(10, result.alocQty());
        assertEquals(80, partly.getAlocQty());
    }

    @Test
    @DisplayName("이미 할당된 만큼은 빼고 잔여요청부터 시작한다 — 재할당해도 과할당이 없다")
    void reallocationStartsFromRemaining() {
        Inv only = inv(1L, 100, LocalDate.of(2026, 12, 31));
        candidates(only);
        OutbLine line = line(1L, 30);
        targetLines(line);
        when(outbAllocRepository.sumAlocQtyByLineIds(anyList())).thenReturn(Map.of(1L, 20L));

        AllocExecuteResponse result = execute();

        assertEquals(10, result.reqQty());
        assertEquals(10, result.alocQty());
    }

    @Test
    @DisplayName("같은 (라인, 재고) 조합은 새 행을 만들지 않고 기존 행에 합산한다")
    void mergesIntoExistingAlloc() {
        Inv only = inv(1L, 100, LocalDate.of(2026, 12, 31));
        candidates(only);
        OutbLine line = line(1L, 30);
        targetLines(line);
        OutbAlloc existing = OutbAlloc.builder().outbLine(line).inv(only).alocQty(20L).build();
        when(outbAllocRepository.sumAlocQtyByLineIds(anyList())).thenReturn(Map.of(1L, 20L));
        when(outbAllocRepository.findByOutbLineIdIn(anyList())).thenReturn(List.of(existing));

        execute();

        assertEquals(30, existing.getAlocQty());
        verify(outbAllocRepository, never()).save(any(OutbAlloc.class));
    }

    // ── 잔여수명 필터 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("잔여수명이 점포 기준에 못 미치는 Lot은 제외하고 사유를 남긴다")
    void filtersByStoreLifeRate() {
        // 제조 2026-01-01 ~ 만료 2026-09-01 = 243일, 출고예정일 기준 잔여 22일 → 9.0% < 50%
        Inv aged = inv(1L, 100, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1));
        candidates(aged);
        targetLines(line(1L, 10));

        AllocExecuteResponse result = execute();

        assertEquals(0, result.alocQty());
        assertEquals(1, lineResult(result, 1L).skips().size());
        assertTrue(lineResult(result, 1L).skips().get(0).reason().contains("잔여수명"));
    }

    @Test
    @DisplayName("유통기한 미관리 Lot은 필터 대상이 아니라 그대로 할당된다")
    void unmanagedLotPassesFilter() {
        Inv noLot = inv(1L, 100, null, null);
        candidates(noLot);
        targetLines(line(1L, 10));

        assertEquals(10, execute().alocQty());
    }

    @Test
    @DisplayName("기한이 지난 Lot은 점포 기준이 0이어도 제외한다 — 비율과 별개의 하드 가드")
    void expiredLotAlwaysExcluded() {
        when(store.getOutbLifeRate()).thenReturn((short) 0);
        Inv expired = inv(1L, 100, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 1));
        candidates(expired);
        targetLines(line(1L, 10));

        AllocExecuteResponse result = execute();

        assertEquals(0, result.alocQty());
        assertTrue(lineResult(result, 1L).skips().get(0).reason().contains("유통기한 경과"));
    }

    @Test
    @DisplayName("잔여수명은 상품 마스터가 아니라 Lot의 두 날짜로 잰다")
    void lifeRateUsesLotDatesOnly() {
        // 제조 2026-01-01 ~ 만료 2027-01-01 = 365일, 잔여 144일 → 39.4% < 50% → 제외.
        // 분모를 상품 마스터의 유통기한일수로 바꿔도 이 판정이 흔들리면 안 된다.
        Inv aged = inv(1L, 100, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        candidates(aged);
        targetLines(line(1L, 10));

        assertEquals(0, execute().alocQty());
        verify(prod, never()).getShelfLifeDays();
    }

    // ── 수동할당 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("수동할당은 잔여수명 미달 Lot도 받는다 — 차단이 아니라 화면 경고다")
    void manualIgnoresLifeFilter() {
        Inv aged = inv(1L, 100, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1));
        OutbLine line = line(1L, 10);
        when(outbLineRepository.findById(1L)).thenReturn(Optional.of(line));

        AllocExecuteResponse result = outbAllocService.allocateManual(100L, manual(1L, 1L, 10L));

        assertEquals(10, result.alocQty());
        assertEquals(10, aged.getAlocQty());
    }

    @Test
    @DisplayName("여러 행의 합계로 과할당을 판정한다 — 첫 행만 보지 않는다")
    void manualValidatesAllRowsForOverAllocation() {
        inv(1L, 100, LocalDate.of(2026, 12, 31));
        inv(2L, 100, LocalDate.of(2026, 12, 31));
        OutbLine line = line(1L, 10);
        when(outbLineRepository.findById(1L)).thenReturn(Optional.of(line));

        // 행 하나만 보면 6 <= 10 이라 둘 다 통과하지만, 합계 12는 주문수량 10을 넘는다
        ManualAllocRequest request = manual(1L, 1L, 6L);
        request.getItems().add(item(1L, 2L, 6L));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> outbAllocService.allocateManual(100L, request));
        assertTrue(e.getMessage().contains("주문수량을 초과"));
    }

    @Test
    @DisplayName("여러 행의 합계로 가용재고 초과를 판정한다")
    void manualValidatesAllRowsForAvailability() {
        inv(1L, 10, LocalDate.of(2026, 12, 31));
        OutbLine first = line(1L, 20, "OB-20260803-001");
        OutbLine second = line(2L, 20, "OB-20260803-002");
        when(outbLineRepository.findById(1L)).thenReturn(Optional.of(first));
        when(outbLineRepository.findById(2L)).thenReturn(Optional.of(second));

        ManualAllocRequest request = manual(1L, 1L, 6L);
        request.getItems().add(item(2L, 1L, 6L));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> outbAllocService.allocateManual(100L, request));
        assertTrue(e.getMessage().contains("가용재고를 초과"));
    }

    @Test
    @DisplayName("다른 웨이브의 라인은 거부한다 — 실행 단위가 웨이브이기 때문")
    void manualRejectsLineOfAnotherWave() {
        inv(1L, 100, LocalDate.of(2026, 12, 31));
        OutbWave other = OutbWave.builder().wavNo("WV-20260803-002").build();
        setId(other, 200L);
        OutbLine line = line(1L, 10);
        line.getOutbOrder().unassignWave();
        line.getOutbOrder().assignWave(other, WavRegTyp.MANUAL);
        when(outbLineRepository.findById(1L)).thenReturn(Optional.of(line));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> outbAllocService.allocateManual(100L, manual(1L, 1L, 10L)));
        assertTrue(e.getMessage().contains("이 웨이브에 편성된"));
    }

    // ── 할당해제 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("해제하면 예약이 풀리고, 할당이 0건이 된 주문은 CREATED로 돌아간다")
    void releaseRestoresReservationAndStatus() {
        Inv target = inv(1L, 100, LocalDate.of(2026, 12, 31));
        target.reserve(30);
        OutbLine line = line(1L, 30);
        line.getOutbOrder().allocate();
        OutbAlloc alloc = OutbAlloc.builder().outbLine(line).inv(target).alocQty(30L).build();
        setId(alloc, 500L);
        when(outbAllocRepository.findAllWithLineByIds(List.of(500L))).thenReturn(List.of(alloc));
        when(outbAllocRepository.countByOutbOrderId(any())).thenReturn(0L);

        outbAllocService.release(release(500L));

        assertEquals(0, target.getAlocQty());
        assertEquals(100, target.avalQty());
        assertEquals(OutbStatus.CREATED, line.getOutbOrder().getStatus());
        verify(outbAllocRepository).deleteAll(List.of(alloc));
    }

    @Test
    @DisplayName("할당이 남아 있으면 주문은 ALLOCATED로 유지한다")
    void releaseKeepsStatusWhenAllocRemains() {
        Inv target = inv(1L, 100, LocalDate.of(2026, 12, 31));
        target.reserve(30);
        OutbLine line = line(1L, 50);
        line.getOutbOrder().allocate();
        OutbAlloc alloc = OutbAlloc.builder().outbLine(line).inv(target).alocQty(10L).build();
        setId(alloc, 500L);
        when(outbAllocRepository.findAllWithLineByIds(List.of(500L))).thenReturn(List.of(alloc));
        when(outbAllocRepository.countByOutbOrderId(any())).thenReturn(1L);

        outbAllocService.release(release(500L));

        assertEquals(20, target.getAlocQty());
        assertEquals(OutbStatus.ALLOCATED, line.getOutbOrder().getStatus());
    }

    @Test
    @DisplayName("피킹이 시작된 할당은 해제할 수 없다 — 차수 컬럼 없이 이 조건 하나로 판정한다")
    void releaseRejectsPickedAlloc() {
        Inv target = inv(1L, 100, LocalDate.of(2026, 12, 31));
        OutbLine line = line(1L, 30);
        OutbAlloc alloc = mock(OutbAlloc.class);
        when(alloc.releasable()).thenReturn(false);
        when(alloc.getPikngQty()).thenReturn(5L);
        when(alloc.getOutbLine()).thenReturn(line);
        when(outbAllocRepository.findAllWithLineByIds(List.of(500L))).thenReturn(List.of(alloc));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> outbAllocService.release(release(500L)));

        assertTrue(e.getMessage().contains("피킹이 시작된"));
        assertEquals(0, target.getAlocQty());
        verify(outbAllocRepository, never()).deleteAll(anyList());
    }

    // ── 입력 검증 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("웨이브를 고르지 않으면 실행하지 않는다")
    void requiresWave() {
        assertThrows(IllegalArgumentException.class, () -> outbAllocService.execute(new AllocExecuteRequest()));
    }

    @Test
    @DisplayName("잔량이 남은 라인이 없으면 실행하지 않는다")
    void requiresTargetLines() {
        when(outbAllocRepository.findTargetLines(anyList())).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, this::execute);
    }

    // ── 픽스처 ───────────────────────────────────────────────────────────────

    private AllocExecuteResponse execute() {
        AllocExecuteRequest request = new AllocExecuteRequest();
        request.setWavIds(List.of(100L));
        return outbAllocService.execute(request);
    }

    private AllocExecuteResponse.LineResult lineResult(AllocExecuteResponse response, Long lineId) {
        return response.lines().stream().filter(l -> l.outbLineId().equals(lineId)).findFirst().orElseThrow();
    }

    private void candidates(Inv... candidates) {
        List<Long> ids = new ArrayList<>();
        for (Inv candidate : candidates) {
            ids.add(candidate.getId());
        }
        when(outbAllocRepository.findCandidateIds(1L)).thenReturn(ids);
    }

    private void targetLines(OutbLine... lines) {
        when(outbAllocRepository.findTargetLines(anyList())).thenReturn(List.of(lines));
    }

    /**
     * 유통기한만 주면 <b>갓 만든 Lot</b>으로 둔다 — 제조일자를 출고예정일 직전에 놓아
     * 잔여율이 점포 기준을 넉넉히 넘게 한다. 잔여수명 필터를 보는 테스트는 두 날짜를 직접 준다.
     */
    private Inv inv(long id, long onHand, LocalDate expiryDt) {
        return inv(id, onHand, expiryDt != null ? EXPCT_DE.minusDays(10) : null, expiryDt);
    }

    private Inv inv(long id, long onHand, LocalDate mfgDt, LocalDate expiryDt) {
        Lot lot = mock(Lot.class);
        when(lot.getId()).thenReturn(id);
        when(lot.getLotNo()).thenReturn("LOT-" + id);
        when(lot.getMfgDt()).thenReturn(mfgDt);
        when(lot.getExpiryDt()).thenReturn(expiryDt);

        Loc loc = mock(Loc.class);
        when(loc.getId()).thenReturn(id);
        when(loc.getLocCd()).thenReturn("A-01-" + id);
        when(loc.getLocTyp()).thenReturn(LocTyp.STORAGE);

        Inv created = Inv.builder().prod(prod).loc(loc).lot(lot).build();
        setId(created, id);
        created.increaseOnHand(onHand);
        invById.put(id, created);
        return created;
    }

    private OutbLine line(long id, long odrQty) {
        return line(id, odrQty, "OB-20260803-00" + id);
    }

    private OutbLine line(long id, long odrQty, String outbNo) {
        OutbOrder order = OutbOrder.builder()
                .outbNo(outbNo).omsOutbOrderId(++seq).store(store)
                .odrDe(LocalDate.of(2026, 8, 3)).expctDe(EXPCT_DE).outbTyp("NRML")
                .build();
        setId(order, id);
        OutbLine created = OutbLine.builder().prod(prod).odrQty(odrQty).build();
        setId(created, id);
        order.addLine(created);
        order.assignWave(wave, WavRegTyp.MANUAL);
        return created;
    }

    private ManualAllocRequest manual(long lineId, long invId, long qty) {
        ManualAllocRequest request = new ManualAllocRequest();
        request.setItems(new ArrayList<>(List.of(item(lineId, invId, qty))));
        return request;
    }

    private ManualAllocRequest.Item item(long lineId, long invId, long qty) {
        ManualAllocRequest.Item created = new ManualAllocRequest.Item();
        created.setOutbLineId(lineId);
        created.setInvId(invId);
        created.setQty(qty);
        return created;
    }

    private AllocReleaseRequest release(Long... allocIds) {
        AllocReleaseRequest request = new AllocReleaseRequest();
        request.setAllocIds(List.of(allocIds));
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
