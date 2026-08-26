package com.project.wmsback.inventory.service;

import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.inventory.dto.InvAdjRequest;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvAdj;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.InvHld;
import com.project.wmsback.inventory.entity.InvHldRlzAcrst;
import com.project.wmsback.inventory.entity.InvHldStatus;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvAdjQueryRepository;
import com.project.wmsback.inventory.repository.InvAdjRepository;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvHldAcrstRepository;
import com.project.wmsback.inventory.repository.InvHldRepository;
import com.project.wmsback.inventory.repository.InvHldRlzAcrstRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.LotRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 재고조정의 네 가지 — 가용 라인의 ±, 보류 라인이 해제와 차감을 한 트랜잭션에 묶는 것,
 * 예약·보류 침범 차단, 락과 채번의 순서.
 *
 * 재고 쓰기 포트(InvStore)와 보류 서비스(InvHldService)는 실물을 쓴다 —
 * ADJUST 이력·스냅샷 증감·해제 실적·항등식이 검증 대상이기 때문 (로트변경 테스트와 같은 구성).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvAdjServiceTest {

    private static final long PROD_ID = 1L;
    private static final long LOC_ID = 3L;
    private static final long LOT_ID = 5L;
    private static final long HLD_ID = 9L;
    private static final String ADJ_NO = "AJ-20260826-001";

    @Mock InvRepository invRepository;
    @Mock InvHistRepository invHistRepository;
    @Mock InvAdjRepository invAdjRepository;
    @Mock InvAdjQueryRepository invAdjQueryRepository;
    @Mock InvHldRepository invHldRepository;
    @Mock InvHldAcrstRepository invHldAcrstRepository;
    @Mock InvHldRlzAcrstRepository invHldRlzAcrstRepository;
    @Mock ProdRepository prodRepository;
    @Mock LocRepository locRepository;
    @Mock LotRepository lotRepository;
    @Mock CodeDetailRepository codeDetailRepository;
    @Mock NbrService nbrService;

    private InvAdjService invAdjService;
    private InvStore invStore;

    private Prod prod;
    private Loc loc;
    private Lot lot;
    private Inv inv;

    @BeforeEach
    void setUp() {
        invStore = new InvStore(invRepository, invHistRepository);
        InvHldService invHldService = new InvHldService(invStore, invHldRepository,
                invHldAcrstRepository, invHldRlzAcrstRepository,
                new RsnValidator(codeDetailRepository), nbrService);
        invAdjService = new InvAdjService(invStore, invAdjRepository, invAdjQueryRepository,
                invHldRepository, invHldService, prodRepository, locRepository, lotRepository,
                new RsnValidator(codeDetailRepository), nbrService);

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(PROD_ID);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        when(prodRepository.findById(PROD_ID)).thenReturn(Optional.of(prod));

        loc = mock(Loc.class);
        when(loc.getId()).thenReturn(LOC_ID);
        when(loc.getLocCd()).thenReturn("RT-01-01");
        when(loc.getLocTyp()).thenReturn(LocTyp.STORAGE);
        when(locRepository.findById(LOC_ID)).thenReturn(Optional.of(loc));

        lot = mock(Lot.class);
        when(lot.getId()).thenReturn(LOT_ID);
        when(lot.getLotNo()).thenReturn("LOT-260722-001");
        when(lot.getProd()).thenReturn(prod);
        when(lotRepository.findById(LOT_ID)).thenReturn(Optional.of(lot));

        inv = Inv.builder().prod(prod).loc(loc).lot(lot).build();
        inv.increaseOnHand(50L);

        when(invRepository.findByKeyForUpdate(PROD_ID, LOC_ID, LOT_ID)).thenReturn(Optional.of(inv));
        when(invRepository.save(any(Inv.class))).thenAnswer(a -> a.getArgument(0));

        when(codeDetailRepository.existsById(any())).thenReturn(true);
        when(nbrService.issue(eq("INV_ADJ_NO"), any())).thenReturn(ADJ_NO);
    }

    private InvAdjRequest request(InvAdjRequest.Item... items) {
        InvAdjRequest req = new InvAdjRequest();
        req.setItems(List.of(items));
        return req;
    }

    private InvAdjRequest.Item item(long adjQty, Long hldId) {
        InvAdjRequest.Item item = new InvAdjRequest.Item();
        item.setProdId(PROD_ID);
        item.setLocId(LOC_ID);
        item.setLotId(LOT_ID);
        item.setAdjQty(adjQty);
        item.setHldId(hldId);
        item.setRsnCd("SCRP");
        return item;
    }

    /** 보류 건 하나를 재고에 걸어 둔다 (반품 검수가 불량분에 거는 것과 같은 상태) */
    private InvHld heldOn(long qty) {
        inv.hold(qty);
        InvHld hld = InvHld.builder()
                .hldNo("HD-20260826-001").prod(prod).loc(loc).lot(lot)
                .hldQty(qty).rsnCd("QLTY").build();
        when(invHldRepository.findByIdForUpdate(HLD_ID)).thenReturn(Optional.of(hld));
        return hld;
    }

    @Test
    @DisplayName("가용 라인 감소: 부호 있는 ADJUST 1행과 스냅샷 감소를 남기고, 조정전수량은 락 이후 값이다")
    void adjust_decreasesAvailableWithSignedHistory() {
        List<String> nos = invAdjService.adjust(request(item(-20L, null)));

        assertEquals(List.of(ADJ_NO), nos);
        assertEquals(30L, inv.getOnHandQty());

        ArgumentCaptor<InvHist> histCaptor = ArgumentCaptor.forClass(InvHist.class);
        verify(invHistRepository).save(histCaptor.capture());
        InvHist hist = histCaptor.getValue();
        assertEquals(TxTyp.ADJUST, hist.getTxTyp());
        assertEquals(RefDocTyp.INV_ADJ, hist.getRfnDocTyp());
        assertEquals(ADJ_NO, hist.getRfnDocNo());
        assertEquals(-20L, hist.getQty());
        assertNull(hist.getFromLocId());

        ArgumentCaptor<InvAdj> logCaptor = ArgumentCaptor.forClass(InvAdj.class);
        verify(invAdjRepository).save(logCaptor.capture());
        InvAdj log = logCaptor.getValue();
        assertEquals(50L, log.getAdjBfrQty());
        assertEquals(-20L, log.getAdjQty());
        assertNull(log.getHldNo());
    }

    @Test
    @DisplayName("가용 라인 증가: 재고 행이 없으면 만들어 올리고 조정전수량은 0이다")
    void adjust_increasesAndCreatesRowWhenAbsent() {
        when(invRepository.findByKeyForUpdate(PROD_ID, LOC_ID, LOT_ID)).thenReturn(Optional.empty());

        invAdjService.adjust(request(item(15L, null)));

        ArgumentCaptor<Inv> invCaptor = ArgumentCaptor.forClass(Inv.class);
        verify(invRepository).save(invCaptor.capture());
        assertEquals(15L, invCaptor.getValue().getOnHandQty());

        ArgumentCaptor<InvAdj> logCaptor = ArgumentCaptor.forClass(InvAdj.class);
        verify(invAdjRepository).save(logCaptor.capture());
        assertEquals(0L, logCaptor.getValue().getAdjBfrQty());
        assertEquals(15L, logCaptor.getValue().getAdjQty());
    }

    @Test
    @DisplayName("보류 라인: 해제 실적(사유 ADJ)과 물리 감소가 한 트랜잭션에 함께 남고 hld_qty가 소진된다")
    void adjust_holdLineReleasesAndDecreasesTogether() {
        InvHld hld = heldOn(20L);

        invAdjService.adjust(request(item(-20L, HLD_ID)));

        // 보류가 소진되고 실물도 빠진다 — 가용은 그대로(30)라 「폐기 대기분이 가용으로 뜨는 창」이 없다
        assertEquals(30L, inv.getOnHandQty());
        assertEquals(0L, inv.getHldQty());
        assertEquals(30L, inv.avalQty());
        assertEquals(InvHldStatus.RELEASED, hld.getStatus());

        ArgumentCaptor<InvHldRlzAcrst> rlzCaptor = ArgumentCaptor.forClass(InvHldRlzAcrst.class);
        verify(invHldRlzAcrstRepository).save(rlzCaptor.capture());
        assertEquals(InvHldService.RLZ_RSN_ADJ, rlzCaptor.getValue().getRsnCd());
        assertEquals(20L, rlzCaptor.getValue().getRlzQty());

        ArgumentCaptor<InvAdj> logCaptor = ArgumentCaptor.forClass(InvAdj.class);
        verify(invAdjRepository).save(logCaptor.capture());
        assertEquals("HD-20260826-001", logCaptor.getValue().getHldNo());
        assertEquals(-20L, logCaptor.getValue().getAdjQty());
    }

    @Test
    @DisplayName("보류 라인은 보류 소진을 물리 감소보다 먼저 한다 — 반대면 aloc+hld<=on_hand 위반 상태가 DB에 닿는다")
    void adjust_holdLineReleasesBeforeDecrease() {
        heldOn(50L);

        invAdjService.adjust(request(item(-50L, HLD_ID)));

        // 해제 실적 저장(= 보류 소진 직후) → 이력 저장(= 물리 감소) 순서
        InOrder order = inOrder(invHldRlzAcrstRepository, invHistRepository);
        order.verify(invHldRlzAcrstRepository).save(any(InvHldRlzAcrst.class));
        order.verify(invHistRepository).save(any(InvHist.class));
    }

    @Test
    @DisplayName("보류 라인은 부분 폐기가 가능하고 남은 잔량은 HELD로 남는다")
    void adjust_holdLinePartialKeepsHeld() {
        InvHld hld = heldOn(20L);

        invAdjService.adjust(request(item(-5L, HLD_ID)));

        assertEquals(45L, inv.getOnHandQty());
        assertEquals(15L, inv.getHldQty());
        assertEquals(15L, hld.remainingQty());
        assertEquals(InvHldStatus.HELD, hld.getStatus());
    }

    @Test
    @DisplayName("보류 라인은 그 건의 미해제 잔량을 넘길 수 없다")
    void adjust_holdLineRejectsOverRemaining() {
        heldOn(20L);

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-21L, HLD_ID))));
    }

    @Test
    @DisplayName("보류 라인은 증가 조정을 받지 않는다 — 늘리는 것은 보류 등록의 몫이다")
    void adjust_holdLineRejectsIncrease() {
        heldOn(20L);

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(5L, HLD_ID))));
        verify(invHistRepository, never()).save(any(InvHist.class));
    }

    @Test
    @DisplayName("가용 라인 감소는 예약·보류를 침범할 수 없다 — 보류분을 없애려면 보류 건을 담아야 한다")
    void adjust_availableLineRejectsReservedAndHeld() {
        inv.reserve(10L);
        inv.hold(20L);   // 가용 20

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-21L, null))));
        assertEquals(50L, inv.getOnHandQty());
        verify(invHistRepository, never()).save(any(InvHist.class));
    }

    @Test
    @DisplayName("조정수량 0은 거부한다 — 조정할 것이 없다")
    void adjust_rejectsZeroQty() {
        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(0L, null))));
    }

    @Test
    @DisplayName("같은 재고 행의 가용 라인이 두 번 실리면 거부한다 — 이중 차감이 되어 결과가 요청 순서에 좌우된다")
    void adjust_rejectsDuplicateAvailableLine() {
        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-5L, null), item(-3L, null))));
    }

    @Test
    @DisplayName("같은 재고 행이라도 보류 건이 다르면 함께 담을 수 있다 — 병존하는 보류를 한 번에 폐기한다")
    void adjust_allowsTwoHoldsOnSameInvRow() {
        long otherHldId = 11L;
        inv.hold(10L);
        inv.hold(6L);
        InvHld first = InvHld.builder().hldNo("HD-20260826-001").prod(prod).loc(loc).lot(lot)
                .hldQty(10L).rsnCd("QLTY").build();
        InvHld second = InvHld.builder().hldNo("HD-20260826-002").prod(prod).loc(loc).lot(lot)
                .hldQty(6L).rsnCd("DAMG").build();
        when(invHldRepository.findByIdForUpdate(HLD_ID)).thenReturn(Optional.of(first));
        when(invHldRepository.findByIdForUpdate(otherHldId)).thenReturn(Optional.of(second));
        when(nbrService.issue(eq("INV_ADJ_NO"), any())).thenReturn(ADJ_NO, "AJ-20260826-002");

        List<String> nos = invAdjService.adjust(request(item(-10L, HLD_ID), item(-6L, otherHldId)));

        assertEquals(2, nos.size());
        assertEquals(34L, inv.getOnHandQty());
        assertEquals(0L, inv.getHldQty());
        verify(invHldRlzAcrstRepository, times(2)).save(any(InvHldRlzAcrst.class));
    }

    @Test
    @DisplayName("스테이징 재고는 조정 대상이 아니다 — 보류·이동·조사와 같은 경계")
    void adjust_rejectsNonStorageLocation() {
        when(loc.getLocTyp()).thenReturn(LocTyp.STAGE);

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-5L, null))));
    }

    @Test
    @DisplayName("입고대기·출고대기 존 재고는 로케이션 유형이 보관이어도 조정 대상이 아니다")
    void adjust_rejectsStagingZonEvenWhenStorageLocType() {
        for (BizDvsn dvsn : BizDvsn.STAGING) {
            Zon stagingZon = mock(Zon.class);
            when(stagingZon.getBizDvsn()).thenReturn(dvsn);
            when(loc.getZon()).thenReturn(stagingZon);
            when(loc.getLocTyp()).thenReturn(LocTyp.STORAGE);   // 유형 필터는 통과하는 상태

            assertThrows(IllegalArgumentException.class,
                    () -> invAdjService.adjust(request(item(-5L, null))));
        }
        verify(invHistRepository, never()).save(any(InvHist.class));
    }

    @Test
    @DisplayName("존이 등록되지 않은 로케이션은 대기존이 아니다 — FK가 없어 생길 수 있는 상태")
    void adjust_allowsLocWithoutZon() {
        when(loc.getZon()).thenReturn(null);

        invAdjService.adjust(request(item(-5L, null)));

        assertEquals(45L, inv.getOnHandQty());
    }

    @Test
    @DisplayName("존재하지 않는 재고는 감소 조정할 수 없다")
    void adjust_rejectsDecreaseOnMissingRow() {
        when(invRepository.findByKeyForUpdate(PROD_ID, LOC_ID, LOT_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-5L, null))));
    }

    @Test
    @DisplayName("채번은 재고 행 락과 보류 건 락을 모두 잡은 뒤다 — 공유 카운터 락이 재고 락 사이에 끼면 안 된다")
    void adjust_issuesNumberAfterAllLocks() {
        heldOn(20L);

        invAdjService.adjust(request(item(-20L, HLD_ID)));

        InOrder order = inOrder(invRepository, invHldRepository, nbrService);
        order.verify(invRepository).findByKeyForUpdate(PROD_ID, LOC_ID, LOT_ID);
        order.verify(invHldRepository).findByIdForUpdate(HLD_ID);
        order.verify(nbrService).issue(eq("INV_ADJ_NO"), any());
    }

    @Test
    @DisplayName("수량 검증에 걸리면 채번하지 않는다 — 실패할 요청이 공유 카운터 락을 쥐고 있으면 안 된다")
    void adjust_doesNotIssueNumberWhenValidationFails() {
        inv.hold(45L);   // 가용 5

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-6L, null))));
        verify(nbrService, never()).issue(eq("INV_ADJ_NO"), any());
    }

    @Test
    @DisplayName("보류 잔량 검증에 걸려도 채번하지 않는다")
    void adjust_doesNotIssueNumberWhenHoldRemainingExceeded() {
        heldOn(20L);

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-21L, HLD_ID))));
        verify(nbrService, never()).issue(eq("INV_ADJ_NO"), any());
    }

    @Test
    @DisplayName("보류 건이 가리키는 재고와 조정 대상이 다르면 거부하고 아무것도 건드리지 않는다")
    void adjust_rejectsHoldPointingElsewhere() {
        Loc otherLoc = mock(Loc.class);
        when(otherLoc.getId()).thenReturn(999L);
        InvHld hld = InvHld.builder()
                .hldNo("HD-20260826-009").prod(prod).loc(otherLoc).lot(lot)
                .hldQty(5L).rsnCd("QLTY").build();
        when(invHldRepository.findByIdForUpdate(HLD_ID)).thenReturn(Optional.of(hld));

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-1L, HLD_ID))));
        verify(invHldRlzAcrstRepository, never()).save(any(InvHldRlzAcrst.class));
        verify(invHistRepository, never()).save(any(InvHist.class));
    }

    @Test
    @DisplayName("Lot이 그 상품의 것이 아니면 거부한다 — 재고 키 자체가 틀렸다")
    void adjust_rejectsLotProdMismatch() {
        Prod other = mock(Prod.class);
        when(other.getId()).thenReturn(99L);
        when(lot.getProd()).thenReturn(other);

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-5L, null))));
    }

    @Test
    @DisplayName("전량 조정으로 수량이 모두 0이 되면 재고 행이 지워진다")
    void adjust_purgesEmptyRow() {
        invAdjService.adjust(request(item(-50L, null)));

        assertEquals(0L, inv.getOnHandQty());
        assertTrue(inv.isEmpty());
        verify(invRepository).delete(inv);
    }

    @Test
    @DisplayName("조정 대상이 비어 있으면 거부한다")
    void adjust_rejectsEmptyRequest() {
        InvAdjRequest req = new InvAdjRequest();
        req.setItems(List.of());
        assertThrows(IllegalArgumentException.class, () -> invAdjService.adjust(req));
    }

    @Test
    @DisplayName("존재하지 않는 보류 건은 거부한다")
    void adjust_rejectsMissingHold() {
        when(invHldRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> invAdjService.adjust(request(item(-5L, HLD_ID))));
    }
}
