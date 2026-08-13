package com.project.wmsback.inventory.service;

import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.inventory.dto.InvLotChngRequest;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.InvLotChng;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvLotChngQueryRepository;
import com.project.wmsback.inventory.repository.InvLotChngRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LotRepository;
import com.project.wmsback.warehouse.service.LotIssuer;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 재고 로트변경의 세 가지 — 목적지 확보(분할/병합/거부) · 수량 규칙 · 락과 채번의 순서.
 * 재고 쓰기 포트(InvStore)와 Lot 채번 포트(LotIssuer)는 실물을 쓴다 —
 * ADJUST 2행·스냅샷 증감·배치 재사용이 검증 대상이기 때문 (검수 테스트와 같은 구성).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvLotChngServiceTest {

    private static final long INV_ID = 100L;
    private static final long PROD_ID = 1L;
    private static final long LOC_ID = 3L;
    private static final long FROM_LOT_ID = 5L;
    private static final LocalDate RECEIPT_DT = LocalDate.of(2026, 7, 22);
    private static final LocalDate FROM_MFG_DT = LocalDate.of(2026, 7, 20);
    private static final LocalDate NEW_MFG_DT = LocalDate.of(2026, 7, 18);
    private static final LocalDate NEW_EXPIRY_DT = LocalDate.of(2026, 8, 17);

    @Mock InvRepository invRepository;
    @Mock InvHistRepository invHistRepository;
    @Mock InvLotChngRepository invLotChngRepository;
    @Mock InvLotChngQueryRepository invLotChngQueryRepository;
    @Mock ProdRepository prodRepository;
    @Mock LotRepository lotRepository;
    @Mock CodeDetailRepository codeDetailRepository;
    @Mock NbrService nbrService;

    private InvLotChngService invLotChngService;

    private Prod prod;
    private Loc loc;
    private Lot fromLot;
    private Inv fromInv;

    @BeforeEach
    void setUp() {
        invLotChngService = new InvLotChngService(
                new InvStore(invRepository, invHistRepository), invRepository,
                invLotChngRepository, invLotChngQueryRepository,
                prodRepository, lotRepository, new LotIssuer(lotRepository),
                new RsnValidator(codeDetailRepository), nbrService);

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(PROD_ID);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        when(prod.getShelfLifeDays()).thenReturn(30);
        when(prodRepository.findByIdForUpdate(PROD_ID)).thenReturn(Optional.of(prod));

        loc = mock(Loc.class);
        when(loc.getId()).thenReturn(LOC_ID);
        when(loc.getLocCd()).thenReturn("A-01-01");
        when(loc.getLocTyp()).thenReturn(LocTyp.STORAGE);

        fromLot = mock(Lot.class);
        when(fromLot.getId()).thenReturn(FROM_LOT_ID);
        when(fromLot.getLotNo()).thenReturn("LOT-260722-001");
        when(fromLot.getReceiptDt()).thenReturn(RECEIPT_DT);
        when(fromLot.getMfgDt()).thenReturn(FROM_MFG_DT);
        when(fromLot.getExpiryDt()).thenReturn(LocalDate.of(2026, 8, 19));
        when(lotRepository.findByIdForUpdate(FROM_LOT_ID)).thenReturn(Optional.of(fromLot));

        fromInv = Inv.builder().prod(prod).loc(loc).lot(fromLot).build();
        fromInv.increaseOnHand(50L);

        when(invRepository.findLockKeysByIdIn(any()))
                .thenReturn(List.of(new InvLockKey(INV_ID, PROD_ID, LOC_ID, FROM_LOT_ID)));
        when(invRepository.findByKeyForUpdate(PROD_ID, LOC_ID, FROM_LOT_ID)).thenReturn(Optional.of(fromInv));
        when(invRepository.findByKeyForUpdate(eq(PROD_ID), eq(LOC_ID), anyLong()))
                .thenAnswer(a -> a.getArgument(2, Long.class).equals(FROM_LOT_ID) ? Optional.of(fromInv) : Optional.empty());
        when(invRepository.save(any(Inv.class))).thenAnswer(a -> a.getArgument(0));

        // 목적지 배치 기본값: 없음 → 채번·생성 (분할). 생성 Lot의 id는 DB identity를 흉내 낸다
        when(lotRepository.findAllByBatchKey(anyLong(), any(), any())).thenReturn(List.of());
        when(lotRepository.countByProdIdAndReceiptDt(PROD_ID, RECEIPT_DT)).thenReturn(1L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> {
            Lot saved = a.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 77L);
            return saved;
        });

        when(codeDetailRepository.existsById(any())).thenReturn(true);
        when(nbrService.issue(eq("LOT_CHNG_NO"), any())).thenReturn("LC-20260813-001");
    }

    private InvLotChngRequest request(InvLotChngRequest.Item... items) {
        InvLotChngRequest req = new InvLotChngRequest();
        req.setItems(List.of(items));
        return req;
    }

    private InvLotChngRequest.Item item(long invId, long qty, LocalDate mfgDt, LocalDate expiryDt) {
        InvLotChngRequest.Item item = new InvLotChngRequest.Item();
        item.setInvId(invId);
        item.setChngQty(qty);
        item.setMfgDt(mfgDt);
        item.setExpiryDt(expiryDt);
        item.setRsnCd("MISPRT");
        return item;
    }

    @Test
    @DisplayName("분할: 목적지 배치가 없으면 채번·생성하고, 원 −N / 새 Lot +N의 ADJUST 2행과 스냅샷 증감을 남긴다")
    void change_splitsIntoNewLotWithAdjustPair() {
        List<String> nos = invLotChngService.change(request(item(INV_ID, 20L, NEW_MFG_DT, NEW_EXPIRY_DT)));

        assertEquals(List.of("LC-20260813-001"), nos);
        assertEquals(30L, fromInv.getOnHandQty());

        // 생성된 목적지 Lot — 유통기한은 계산이 아니라 화면 입력값 그대로다
        ArgumentCaptor<Lot> lotCaptor = ArgumentCaptor.forClass(Lot.class);
        verify(lotRepository).save(lotCaptor.capture());
        assertEquals(NEW_MFG_DT, lotCaptor.getValue().getMfgDt());
        assertEquals(NEW_EXPIRY_DT, lotCaptor.getValue().getExpiryDt());
        assertEquals(RECEIPT_DT, lotCaptor.getValue().getReceiptDt());

        // ADJUST 2행 — 실물 무이동이라 from/to loc 없음, 참조는 LOT_CHNG + 로트변경 번호
        ArgumentCaptor<InvHist> histCaptor = ArgumentCaptor.forClass(InvHist.class);
        verify(invHistRepository, org.mockito.Mockito.times(2)).save(histCaptor.capture());
        List<InvHist> hists = histCaptor.getAllValues();
        assertEquals(-20L, hists.get(0).getQty());
        assertEquals(20L, hists.get(1).getQty());
        for (InvHist hist : hists) {
            assertEquals(TxTyp.ADJUST, hist.getTxTyp());
            assertEquals(RefDocTyp.LOT_CHNG, hist.getRfnDocTyp());
            assertEquals("LC-20260813-001", hist.getRfnDocNo());
        }

        // 실적은 자기완결 스냅샷 — 분할이므로 toLotNewYn = true
        ArgumentCaptor<InvLotChng> logCaptor = ArgumentCaptor.forClass(InvLotChng.class);
        verify(invLotChngRepository).save(logCaptor.capture());
        InvLotChng log = logCaptor.getValue();
        assertEquals("LOT-260722-001", log.getFromLotNo());
        assertEquals(FROM_MFG_DT, log.getFromMfgDt());
        assertEquals(NEW_MFG_DT, log.getToMfgDt());
        assertEquals(NEW_EXPIRY_DT, log.getToExpiryDt());
        assertEquals(20L, log.getChngQty());
        assertTrue(log.getToLotNewYn());
    }

    @Test
    @DisplayName("병합: 같은 배치 키의 Lot이 이미 있고 유통기한이 일치하면 채번 없이 그 Lot으로 합친다")
    void change_mergesIntoExistingBatchLot() {
        Lot destLot = mock(Lot.class);
        when(destLot.getId()).thenReturn(88L);
        when(destLot.getLotNo()).thenReturn("LOT-260722-002");
        when(destLot.getMfgDt()).thenReturn(NEW_MFG_DT);
        when(destLot.getExpiryDt()).thenReturn(NEW_EXPIRY_DT);
        when(lotRepository.findAllByBatchKey(PROD_ID, RECEIPT_DT, NEW_MFG_DT)).thenReturn(List.of(destLot));

        invLotChngService.change(request(item(INV_ID, 20L, NEW_MFG_DT, NEW_EXPIRY_DT)));

        verify(lotRepository, never()).save(any());
        ArgumentCaptor<InvLotChng> logCaptor = ArgumentCaptor.forClass(InvLotChng.class);
        verify(invLotChngRepository).save(logCaptor.capture());
        assertFalse(logCaptor.getValue().getToLotNewYn());
        assertEquals("LOT-260722-002", logCaptor.getValue().getToLotNo());
    }

    @Test
    @DisplayName("거부: 목적지 배치의 Lot이 이미 있는데 유통기한이 입력과 다르면 실제 값을 안내하며 거부한다")
    void change_rejectsWhenExistingBatchExpiryMismatches() {
        Lot destLot = mock(Lot.class);
        when(destLot.getId()).thenReturn(88L);
        when(destLot.getLotNo()).thenReturn("LOT-260722-002");
        when(destLot.getExpiryDt()).thenReturn(LocalDate.of(2026, 8, 20)); // 입력(8/17)과 다르다
        when(lotRepository.findAllByBatchKey(PROD_ID, RECEIPT_DT, NEW_MFG_DT)).thenReturn(List.of(destLot));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> invLotChngService.change(request(item(INV_ID, 20L, NEW_MFG_DT, NEW_EXPIRY_DT))));

        assertTrue(e.getMessage().contains("2026-08-20"));
        verify(invHistRepository, never()).save(any());
        verify(invLotChngRepository, never()).save(any());
    }

    @Test
    @DisplayName("거부: 제조일자가 원 Lot과 같으면 목적지가 원 Lot 자신이라 무의미하다 (순수 분할 미지원)")
    void change_rejectsSameMfgDtAsFromLot() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> invLotChngService.change(request(item(INV_ID, 20L, FROM_MFG_DT, NEW_EXPIRY_DT))));

        assertTrue(e.getMessage().contains("원 Lot과 같습니다"));
        verify(lotRepository, never()).save(any());
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("거부: 변경수량이 가용재고(보유 - 예약 - 보류)를 초과하면 예약·보류를 침범하지 않는다")
    void change_rejectsQtyOverAvailable() {
        fromInv.reserve(35L);
        fromInv.hold(10L); // 가용 = 50 - 35 - 10 = 5

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> invLotChngService.change(request(item(INV_ID, 6L, NEW_MFG_DT, NEW_EXPIRY_DT))));

        assertTrue(e.getMessage().contains("가용 5"));
        assertEquals(50L, fromInv.getOnHandQty());
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("거부: 스테이징 재고는 대상이 아니다 — 적치 잔량 집계가 깨진다 (검수 취소 후 재검수가 정답)")
    void change_rejectsStagingInventory() {
        when(loc.getLocTyp()).thenReturn(LocTyp.STAGE);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> invLotChngService.change(request(item(INV_ID, 20L, NEW_MFG_DT, NEW_EXPIRY_DT))));

        assertTrue(e.getMessage().contains("보관 로케이션"));
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("거부: 유통기한 미관리 상품의 재고는 대상이 아니다 (Lot의 두 날짜가 항상 null인 것이 정의)")
    void change_rejectsNonShelfLifeProd() {
        when(prod.getShelfLifeDays()).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> invLotChngService.change(request(item(INV_ID, 20L, NEW_MFG_DT, NEW_EXPIRY_DT))));

        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("거부: 같은 재고 행을 두 번 실으면 이중 차감이 되므로 애초에 거부한다")
    void change_rejectsDuplicateInvRow() {
        assertThrows(IllegalArgumentException.class, () -> invLotChngService.change(request(
                item(INV_ID, 10L, NEW_MFG_DT, NEW_EXPIRY_DT),
                item(INV_ID, 10L, NEW_MFG_DT, NEW_EXPIRY_DT))));

        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("락·채번 순서: 상품 → 원 Lot → inv 행, 채번은 재고 락을 전부 잡은 뒤다 (전역 락 계층)")
    void change_locksProdThenLotThenInvAndIssuesNoLast() {
        invLotChngService.change(request(item(INV_ID, 20L, NEW_MFG_DT, NEW_EXPIRY_DT)));

        InOrder inOrder = inOrder(prodRepository, lotRepository, invRepository, nbrService);
        inOrder.verify(prodRepository).findByIdForUpdate(PROD_ID);
        inOrder.verify(lotRepository).findByIdForUpdate(FROM_LOT_ID);
        inOrder.verify(invRepository).findByKeyForUpdate(PROD_ID, LOC_ID, FROM_LOT_ID);
        inOrder.verify(nbrService).issue(eq("LOT_CHNG_NO"), any());
    }
}
