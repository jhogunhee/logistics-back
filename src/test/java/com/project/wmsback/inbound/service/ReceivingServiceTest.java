package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.ReceiveRequest;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbStatus;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.service.InvHldService;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.LotRepository;
import com.project.wmsback.warehouse.service.LotIssuer;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.strategy.inspection.service.InspectionService;
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

import java.time.LocalDate;
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
 * 검수 저장의 세 가지 — 수량 규칙 · Lot 확보 · 락 순서.
 * 수량은 입력이 입고단위(발주단위) 개수, 저장이 낱개(EA) 환산값이다. 환산은 Prod.toEaQty가 하므로
 * 여기서는 목으로 두고 "환산값이 세 곳(라인 누계 · 스냅샷 · 이력)에 같은 값으로 반영되는가"와
 * 과입고 차단만 본다. Lot은 배치 재사용이 빗나갔을 때의 채번 · 제조일자 규칙을 본다.
 * 락은 순서(상품 id 오름차순)와 위치(라인을 읽기 전)를 본다 — ReceivingService#lockProds의 ①②다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceivingServiceTest {

    @Mock IbOrderRepository ibOrderRepository;
    @Mock IbLineRepository ibLineRepository;
    @Mock LotRepository lotRepository;
    @Mock LocRepository locRepository;
    @Mock InvRepository invRepository;
    @Mock InvHistRepository invHistRepository;
    @Mock ProdRepository prodRepository;
    @Mock InspectionService inspectionService;
    @Mock RtngsLocResolver rtngsLocResolver;
    @Mock InvHldService invHldService;

    // 재고 쓰기 포트는 목이 아니라 실물을 쓴다 — 스냅샷 증감·이력 기록이 검증 대상이기 때문
    private ReceivingService receivingService;

    private IbOrder order;
    private IbLine ibLine;
    private Prod prod;
    private Loc staging;
    private Lot lot;
    private Inv inv;

    @BeforeEach
    void setUp() {
        // Lot 채번·재사용 포트도 실물을 쓴다 — 배치 재사용이 빗나갔을 때의 채번·저장이 검증 대상이기 때문
        receivingService = new ReceivingService(ibOrderRepository, ibLineRepository, new LotIssuer(lotRepository),
                locRepository, invHistRepository, new InvStore(invRepository, invHistRepository),
                prodRepository, inspectionService, rtngsLocResolver, invHldService);

        prod = mock(Prod.class);
        when(prod.getId()).thenReturn(1L);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        when(prod.getShelfLifeDays()).thenReturn(null); // 유통기한 미관리 — 제조일자 없이 검수 가능
        // 입고단위 BOX(24EA): 1박스 = 낱개 24
        when(prod.getInbUomCd()).thenReturn("BOX");
        when(prod.toEaQty(anyLong(), any())).thenAnswer(a -> a.getArgument(0, Long.class) * 24);

        order = mock(IbOrder.class);
        when(order.getId()).thenReturn(10L);
        when(order.getIbNo()).thenReturn("IB-20260804-001");
        when(order.isRtngs()).thenReturn(false);
        when(order.rcvUomCd(prod)).thenReturn("BOX");
        when(ibOrderRepository.findById(10L)).thenReturn(Optional.of(order));

        ibLine = mock(IbLine.class);
        when(ibLine.getId()).thenReturn(100L);
        when(ibLine.getIbOrder()).thenReturn(order);
        when(ibLine.getProd()).thenReturn(prod);
        when(ibLine.getExpctQty()).thenReturn(240L); // 10박스 예정
        when(ibLine.getRcvdQty()).thenReturn(0L);
        when(ibLine.getRjctQty()).thenReturn(0L);
        when(ibLineRepository.findById(100L)).thenReturn(Optional.of(ibLine));
        when(ibLineRepository.findProdIdsByOrderIdAndIdIn(eq(10L), any())).thenReturn(List.of(1L));

        staging = mock(Loc.class);
        when(staging.getId()).thenReturn(5L);
        when(locRepository.findByLocCd("RCV-STAGE")).thenReturn(Optional.of(staging));

        lot = mock(Lot.class);
        when(lot.getId()).thenReturn(7L);
        when(lotRepository.findAllByBatchKey(anyLong(), any(), any()))
                .thenReturn(List.of(lot));

        inv = mock(Inv.class);
        when(invRepository.findByKeyForUpdate(1L, 5L, 7L)).thenReturn(Optional.of(inv));
    }

    private ReceiveRequest request(long inspectQty) {
        return request(line(100L, inspectQty));
    }

    private ReceiveRequest request(ReceiveRequest.Line... lines) {
        ReceiveRequest req = new ReceiveRequest();
        req.setLines(List.of(lines));
        return req;
    }

    private ReceiveRequest.Line line(long ibLineId, long inspectQty) {
        ReceiveRequest.Line line = new ReceiveRequest.Line();
        line.setIbLineId(ibLineId);
        line.setInspectQty(inspectQty);
        line.setReceiptDt(LocalDate.of(2026, 8, 4));
        return line;
    }

    private ReceiveRequest.Line rtngsLine(long ibLineId, Long inspectQty, Long rjctQty, String rsnCd) {
        ReceiveRequest.Line line = line(ibLineId, inspectQty != null ? inspectQty : 0);
        line.setInspectQty(inspectQty);
        line.setRjctQty(rjctQty);
        line.setRjctRsnCd(rsnCd);
        return line;
    }

    private Loc rtngsLoc;
    private Inv rtngsInv;

    private void stubRtngs() {
        when(order.isRtngs()).thenReturn(true);
        when(order.rcvUomCd(prod)).thenReturn("EA");
        rtngsLoc = mock(Loc.class);
        when(rtngsLoc.getId()).thenReturn(9L);
        when(rtngsLocResolver.resolve(prod)).thenReturn(rtngsLoc);
        rtngsInv = mock(Inv.class);
        when(invRepository.findByKeyForUpdate(1L, 9L, 7L)).thenReturn(Optional.of(rtngsInv));
    }

    @Test
    @DisplayName("검수수량은 입고단위 개수로 받아 낱개(EA)로 환산해 누계·스냅샷·이력에 같은 값으로 반영한다")
    void receive_convertsInbUomQtyToEaQty() {
        receivingService.receive(10L, request(5)); // 5박스 = 120

        verify(ibLine).receive(120L);
        verify(inv).increaseOnHand(120L);
        ArgumentCaptor<InvHist> captor = ArgumentCaptor.forClass(InvHist.class);
        verify(invHistRepository).save(captor.capture());
        assertEquals(120L, captor.getValue().getQty());
    }

    @Test
    @DisplayName("환산 수량이 예정 잔량과 정확히 같으면 허용한다 (경계값)")
    void receive_allowsExactRemaining() {
        when(ibLine.getRcvdQty()).thenReturn(120L); // 잔량 120 = 5박스

        receivingService.receive(10L, request(5));

        verify(ibLine).receive(120L);
    }

    @Test
    @DisplayName("환산 수량이 예정 잔량을 초과하면 저장 전체를 거부한다 (과입고 차단)")
    void receive_rejectsOverReceipt() {
        when(ibLine.getRcvdQty()).thenReturn(130L); // 잔량 110 < 5박스(120)

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(5)));

        assertTrue(e.getMessage().contains("PROD-0001"));
        verify(ibLine, never()).receive(anyLong());
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("검수수량이 1 미만이면 거부한다")
    void receive_rejectsQtyBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(0)));

        verify(invHistRepository, never()).save(any());
    }

    /**
     * 적치지시가 예약(aloc)한 몫은 검수 취소로 빼갈 수 없다 — 「미완료 적치지시가 있으면 차단」 특례를
     * 따로 두지 않고 가용재고 검증 하나로 처리한다는 판단의 회귀 방어 (docs/design.md 「검수 취소」).
     */
    @Test
    @DisplayName("검수 취소 거부: 적치지시가 예약한 수량이 있어 가용재고가 모자라면 취소할 수 없다")
    void cancelReceipt_rejectsWhenReservedByPutawayTask() {
        InvHist receipt = mock(InvHist.class);
        when(receipt.getId()).thenReturn(500L);
        when(receipt.getTxTyp()).thenReturn(TxTyp.RECEIVE);
        when(receipt.getIbLineId()).thenReturn(100L);
        when(receipt.getProd()).thenReturn(prod);
        when(receipt.getLoc()).thenReturn(staging);
        when(receipt.getLot()).thenReturn(lot);
        when(receipt.getQty()).thenReturn(30L);
        when(invHistRepository.findById(500L)).thenReturn(Optional.of(receipt));
        when(invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(100L, TxTyp.ADJUST))
                .thenReturn(List.of());
        when(order.getStatus()).thenReturn(IbStatus.RECEIVING);

        // 보유 30 전부를 적치지시가 예약한 상태 — 실물은 남아 있지만 가용은 0
        Inv reserved = Inv.builder().prod(prod).loc(staging).lot(lot).build();
        reserved.increaseOnHand(30L);
        reserved.reserve(30L);
        when(invRepository.findByKeyForUpdate(1L, 5L, 7L)).thenReturn(Optional.of(reserved));

        assertThrows(IllegalStateException.class, () -> receivingService.cancelReceipt(10L, 500L));

        assertEquals(30L, reserved.getOnHandQty());
        verify(ibLine, never()).cancelReceive(anyLong());
    }

    @Test
    @DisplayName("반품 검수 취소: 반품존 검수 건은 cancelReject를 부르고 재고 감소·ADJUST 이력을 남긴다")
    void cancelReceipt_rtngsZoneCancelsReject() {
        Zon rtngsZon = mock(Zon.class);
        when(rtngsZon.getBizDvsn()).thenReturn(BizDvsn.RTNGS);
        Loc rtngsRecLoc = mock(Loc.class);
        when(rtngsRecLoc.getId()).thenReturn(9L);
        when(rtngsRecLoc.getZon()).thenReturn(rtngsZon);

        InvHist receipt = mock(InvHist.class);
        when(receipt.getId()).thenReturn(500L);
        when(receipt.getTxTyp()).thenReturn(TxTyp.RECEIVE);
        when(receipt.getIbLineId()).thenReturn(100L);
        when(receipt.getProd()).thenReturn(prod);
        when(receipt.getLoc()).thenReturn(rtngsRecLoc);
        when(receipt.getLot()).thenReturn(lot);
        when(receipt.getQty()).thenReturn(48L);
        when(invHistRepository.findById(500L)).thenReturn(Optional.of(receipt));
        when(invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(100L, TxTyp.ADJUST))
                .thenReturn(List.of());
        when(order.getStatus()).thenReturn(IbStatus.RECEIVING);

        Inv rtngsInv = Inv.builder().prod(prod).loc(rtngsRecLoc).lot(lot).build();
        rtngsInv.increaseOnHand(48L); // 보류 없음 — 가용 48 전부

        when(invRepository.findByKeyForUpdate(1L, 9L, 7L)).thenReturn(Optional.of(rtngsInv));

        receivingService.cancelReceipt(10L, 500L);

        verify(ibLine).cancelReject(48L);
        verify(ibLine, never()).cancelReceive(anyLong());
        assertEquals(0L, rtngsInv.getOnHandQty());
        ArgumentCaptor<InvHist> captor = ArgumentCaptor.forClass(InvHist.class);
        verify(invHistRepository).save(captor.capture());
        assertEquals(TxTyp.ADJUST, captor.getValue().getTxTyp());
        assertEquals(-48L, captor.getValue().getQty());
    }

    @Test
    @DisplayName("반품 검수 취소 거부: 보류가 아직 걸려 있으면 「보류를 해제한 뒤」로 안내한다")
    void cancelReceipt_rtngsZoneRejectsWhileHeld() {
        Zon rtngsZon = mock(Zon.class);
        when(rtngsZon.getBizDvsn()).thenReturn(BizDvsn.RTNGS);
        Loc rtngsRecLoc = mock(Loc.class);
        when(rtngsRecLoc.getId()).thenReturn(9L);
        when(rtngsRecLoc.getZon()).thenReturn(rtngsZon);

        InvHist receipt = mock(InvHist.class);
        when(receipt.getId()).thenReturn(500L);
        when(receipt.getTxTyp()).thenReturn(TxTyp.RECEIVE);
        when(receipt.getIbLineId()).thenReturn(100L);
        when(receipt.getProd()).thenReturn(prod);
        when(receipt.getLoc()).thenReturn(rtngsRecLoc);
        when(receipt.getLot()).thenReturn(lot);
        when(receipt.getQty()).thenReturn(48L);
        when(invHistRepository.findById(500L)).thenReturn(Optional.of(receipt));
        when(invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(100L, TxTyp.ADJUST))
                .thenReturn(List.of());
        when(order.getStatus()).thenReturn(IbStatus.RECEIVING);

        Inv rtngsInv = Inv.builder().prod(prod).loc(rtngsRecLoc).lot(lot).build();
        rtngsInv.increaseOnHand(48L);
        rtngsInv.hold(48L); // 보류가 아직 살아 있어 가용 0

        when(invRepository.findByKeyForUpdate(1L, 9L, 7L)).thenReturn(Optional.of(rtngsInv));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> receivingService.cancelReceipt(10L, 500L));

        assertTrue(e.getMessage().contains("보류를 해제한 뒤"));
        verify(ibLine, never()).cancelReject(anyLong());
        verify(ibLine, never()).cancelReceive(anyLong());
        assertEquals(48L, rtngsInv.getOnHandQty());
    }

    @Test
    @DisplayName("검수 취소 거부: 확정된 입고는 결품까지 못박힌 뒤라 검수를 취소할 수 없다")
    void cancelReceipt_rejectsConfirmedOrder() {
        InvHist receipt = mock(InvHist.class);
        when(receipt.getId()).thenReturn(500L);
        when(receipt.getTxTyp()).thenReturn(TxTyp.RECEIVE);
        when(receipt.getIbLineId()).thenReturn(100L);
        when(invHistRepository.findById(500L)).thenReturn(Optional.of(receipt));
        when(invHistRepository.findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(100L, TxTyp.ADJUST))
                .thenReturn(List.of());
        when(order.getStatus()).thenReturn(IbStatus.CONFIRMED);

        assertThrows(IllegalStateException.class, () -> receivingService.cancelReceipt(10L, 500L));

        verify(ibLine, never()).cancelReceive(anyLong());
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("재사용할 배치가 없으면 Lot을 만든다 — 번호는 상품별·입고일자별 건수+1 (미관리 상품은 두 날짜 null)")
    void receive_createsLotWithSequentialNo() {
        when(lotRepository.findAllByBatchKey(anyLong(), any(), any())).thenReturn(List.of());
        when(lotRepository.countByProdIdAndReceiptDt(1L, LocalDate.of(2026, 8, 4))).thenReturn(2L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));
        when(invRepository.findByKeyForUpdate(any(), any(), any())).thenReturn(Optional.of(inv));

        receivingService.receive(10L, request(5));

        ArgumentCaptor<Lot> captor = ArgumentCaptor.forClass(Lot.class);
        verify(lotRepository).save(captor.capture());
        Lot created = captor.getValue();
        assertEquals("LOT-260804-003", created.getLotNo());
        assertEquals(LocalDate.of(2026, 8, 4), created.getReceiptDt());
        assertNull(created.getMfgDt());
        assertNull(created.getExpiryDt());
    }

    /**
     * §6-1 파생 쿼리 null 버그의 회귀 방어 — 옛 findByProdIdAndReceiptDtAndMfgDt는 mfgDt에 null을
     * 넘기면 mfg_dt = NULL 바인딩으로 어떤 행도 매치되지 않아 미관리 상품은 증분 검수마다 새 Lot이 생겼다.
     * null 분기를 갖춘 findAllByBatchKey(LotIssuer.find)로 바꾼 뒤에는 2회차가 1회차의 Lot을 재사용한다.
     */
    @Test
    @DisplayName("미관리 상품 증분 검수 2회 — null 배치 키도 재사용 조회에 매치되어 Lot이 1개만 생긴다")
    void receive_reusesNullMfgDtBatchAcrossIncrementalReceipts() {
        when(lotRepository.findAllByBatchKey(1L, LocalDate.of(2026, 8, 4), null))
                .thenReturn(List.of())     // 1회차: 배치 없음 → 채번·생성
                .thenReturn(List.of(lot)); // 2회차: 같은 null 배치 키 매치 → 재사용
        when(lotRepository.countByProdIdAndReceiptDt(1L, LocalDate.of(2026, 8, 4))).thenReturn(0L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));
        when(invRepository.findByKeyForUpdate(any(), any(), any())).thenReturn(Optional.of(inv));

        receivingService.receive(10L, request(5));
        receivingService.receive(10L, request(5));

        verify(lotRepository, times(1)).save(any(Lot.class));
    }

    @Test
    @DisplayName("유통기한 미관리 상품은 제조일자를 보내와도 버린다 — 배치 재사용 조회도 Lot도 null이다")
    void receive_discardsMfgDtForNonShelfLifeProd() {
        when(lotRepository.findAllByBatchKey(anyLong(), any(), any())).thenReturn(List.of());
        when(lotRepository.countByProdIdAndReceiptDt(anyLong(), any())).thenReturn(0L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));
        when(invRepository.findByKeyForUpdate(any(), any(), any())).thenReturn(Optional.of(inv));

        ReceiveRequest.Line line = line(100L, 5);
        line.setMfgDt(LocalDate.of(2026, 8, 1));

        receivingService.receive(10L, request(line));

        verify(lotRepository).findAllByBatchKey(1L, LocalDate.of(2026, 8, 4), null);
        ArgumentCaptor<Lot> captor = ArgumentCaptor.forClass(Lot.class);
        verify(lotRepository).save(captor.capture());
        assertNull(captor.getValue().getMfgDt());
        assertNull(captor.getValue().getExpiryDt());
    }

    @Test
    @DisplayName("유통기한 관리 상품의 Lot은 제조일자 + shelfLifeDays를 유통기한으로 계산해 저장한다")
    void receive_computesExpiryDtFromMfgDt() {
        when(prod.getShelfLifeDays()).thenReturn(10);
        when(lotRepository.findAllByBatchKey(anyLong(), any(), any())).thenReturn(List.of());
        when(lotRepository.countByProdIdAndReceiptDt(anyLong(), any())).thenReturn(0L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));
        when(invRepository.findByKeyForUpdate(any(), any(), any())).thenReturn(Optional.of(inv));

        ReceiveRequest.Line line = line(100L, 5);
        line.setMfgDt(LocalDate.of(2026, 8, 1));

        receivingService.receive(10L, request(line));

        ArgumentCaptor<Lot> captor = ArgumentCaptor.forClass(Lot.class);
        verify(lotRepository).save(captor.capture());
        Lot created = captor.getValue();
        assertEquals("LOT-260804-001", created.getLotNo());
        assertEquals(LocalDate.of(2026, 8, 1), created.getMfgDt());
        assertEquals(LocalDate.of(2026, 8, 11), created.getExpiryDt());
    }

    @Test
    @DisplayName("유통기한 관리 상품인데 제조일자가 없으면 거부한다")
    void receive_rejectsMissingMfgDtForShelfLifeProd() {
        when(prod.getShelfLifeDays()).thenReturn(10);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(5)));

        assertTrue(e.getMessage().contains("제조일자는 필수"));
        verify(lotRepository, never()).save(any());
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("제조일자가 입고일자보다 미래면 거부한다")
    void receive_rejectsMfgDtAfterReceiptDt() {
        when(prod.getShelfLifeDays()).thenReturn(10);
        ReceiveRequest.Line line = line(100L, 5); // 입고일자 2026-08-04
        line.setMfgDt(LocalDate.of(2026, 8, 5));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(line)));

        assertTrue(e.getMessage().contains("미래일 수 없습니다"));
        verify(lotRepository, never()).save(any());
        verify(invHistRepository, never()).save(any());
    }

    @Test
    @DisplayName("상품 로우 락은 조회가 돌려준 순서가 아니라 상품 id 오름차순으로 잡는다 (교착 회피)")
    void receive_locksProdsInAscendingProdIdOrder() {
        // 조회는 상품 id 순서를 보장하지 않는다 — 3이 먼저 나와도 2를 먼저 잠가야 한다
        when(prod.getId()).thenReturn(3L);
        when(invRepository.findByKeyForUpdate(3L, 5L, 7L)).thenReturn(Optional.of(inv));
        when(ibLineRepository.findProdIdsByOrderIdAndIdIn(eq(10L), any())).thenReturn(List.of(3L, 2L));

        Prod otherProd = mock(Prod.class);
        when(otherProd.getId()).thenReturn(2L);
        when(otherProd.getShelfLifeDays()).thenReturn(null); // 목의 Integer 기본값은 null이 아니라 0이라 명시가 필요하다
        when(otherProd.getInbUomCd()).thenReturn("BOX");
        when(otherProd.toEaQty(anyLong(), any())).thenAnswer(a -> a.getArgument(0, Long.class) * 24);

        IbLine ibLine2 = mock(IbLine.class);
        when(ibLine2.getId()).thenReturn(200L);
        when(ibLine2.getIbOrder()).thenReturn(order);
        when(ibLine2.getProd()).thenReturn(otherProd);
        when(ibLine2.getExpctQty()).thenReturn(240L);
        when(ibLine2.getRcvdQty()).thenReturn(0L);
        when(ibLineRepository.findById(200L)).thenReturn(Optional.of(ibLine2));
        when(invRepository.findByKeyForUpdate(2L, 5L, 7L)).thenReturn(Optional.of(mock(Inv.class)));

        receivingService.receive(10L, request(line(100L, 1), line(200L, 1)));

        InOrder inOrder = inOrder(prodRepository);
        inOrder.verify(prodRepository).findByIdForUpdate(2L);
        inOrder.verify(prodRepository).findByIdForUpdate(3L);
    }

    @Test
    @DisplayName("상품 락은 라인을 읽는 모든 것보다 먼저 잡는다 — 검수 제약도 그 뒤다 (잔량 검사 직렬화)")
    void receive_locksProdsBeforeReadingAnyLine() {
        receivingService.receive(10L, request(5));

        // 라인을 먼저 읽어두면 뒤에 락을 잡아도 그 값이 갱신되지 않아 잔량 검사가 낡은 값을 쓴다
        InOrder inOrder = inOrder(prodRepository, inspectionService, ibLineRepository);
        inOrder.verify(prodRepository).findByIdForUpdate(1L);
        inOrder.verify(inspectionService).checkReceive(any(), any());
        inOrder.verify(ibLineRepository).findById(100L);
    }

    @Test
    @DisplayName("반품: 양품은 스테이징 RECEIVE, 불량은 반품존 RECEIVE + 보류 — 이력 2건, rcvd/rjct 각각 누계")
    void rtngs_splitsGoodAndRjct() {
        stubRtngs();

        receivingService.receive(10L, request(rtngsLine(100L, 3L, 2L, "DAMG"))); // 3×24 양품, 2×24 불량

        verify(ibLine).receive(72L);
        verify(ibLine).reject(48L);
        verify(inv).increaseOnHand(72L);
        verify(rtngsInv).increaseOnHand(48L);
        verify(invHldService).holdOn(rtngsInv, 48L, "DAMG", null);
        verify(invHistRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("반품: 불량만 온 라인도 저장된다 (양품 0)")
    void rtngs_rjctOnlyLine() {
        stubRtngs();

        receivingService.receive(10L, request(rtngsLine(100L, null, 2L, "QLTY")));

        verify(ibLine, never()).receive(anyLong());
        verify(ibLine).reject(48L);
        verify(invHldService).holdOn(rtngsInv, 48L, "QLTY", null);
    }

    @Test
    @DisplayName("반품: 양품+불량 합계가 잔량(예정 − 양품누계 − 불량누계)을 넘으면 거부")
    void rtngs_rejectsOverRemaining() {
        stubRtngs();
        when(ibLine.getRcvdQty()).thenReturn(120L);
        when(ibLine.getRjctQty()).thenReturn(96L);   // 잔량 24 = 1개

        assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(rtngsLine(100L, 1L, 1L, "DAMG"))));
        verify(ibLine, never()).receive(anyLong());
    }

    @Test
    @DisplayName("반품: 불량수량이 있으면 사유가 필수")
    void rtngs_requiresRjctRsn() {
        stubRtngs();

        assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(rtngsLine(100L, 1L, 1L, null))));
    }

    @Test
    @DisplayName("정상 입고에 불량수량이 오면 거부 — 정상 검수는 불합격 수량을 두지 않는다")
    void normal_rejectsRjctQty() {
        assertThrows(IllegalArgumentException.class,
                () -> receivingService.receive(10L, request(rtngsLine(100L, 1L, 1L, "DAMG"))));
        verify(invHldService, never()).holdOn(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("반품: 보류는 모든 라인의 재고 처리가 끝난 뒤에 건다 (채번은 재고 락을 전부 잡은 뒤)")
    void rtngs_holdsAfterAllLines() {
        stubRtngs();
        IbLine second = mock(IbLine.class);
        when(second.getId()).thenReturn(101L);
        when(second.getIbOrder()).thenReturn(order);
        when(second.getProd()).thenReturn(prod);
        when(second.getExpctQty()).thenReturn(240L);
        when(second.getRcvdQty()).thenReturn(0L);
        when(second.getRjctQty()).thenReturn(0L);
        when(ibLineRepository.findById(101L)).thenReturn(Optional.of(second));

        receivingService.receive(10L, request(rtngsLine(100L, 0L, 1L, "DAMG"), rtngsLine(101L, 0L, 1L, "DAMG")));

        InOrder inOrder = inOrder(rtngsInv, invHldService);
        inOrder.verify(rtngsInv, times(2)).increaseOnHand(24L);
        inOrder.verify(invHldService, times(2)).holdOn(eq(rtngsInv), eq(24L), eq("DAMG"), any());
    }
}
