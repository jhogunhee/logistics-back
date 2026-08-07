package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.ReceiveRequest;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LocRepository;
import com.project.wmsback.warehouse.repository.LotRepository;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.wmsback.strategy.inspection.service.InspectionService;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 검수 저장의 수량 규칙과 Lot 확보.
 * 수량은 입력이 입고단위(발주단위) 개수, 저장이 낱개(EA) 환산값이다. 환산은 Prod.toEaQty가 하므로
 * 여기서는 목으로 두고 "환산값이 세 곳(라인 누계 · 스냅샷 · 이력)에 같은 값으로 반영되는가"와
 * 과입고 차단만 본다. Lot은 배치 재사용이 빗나갔을 때의 채번 · 제조일자 규칙을 본다.
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
        receivingService = new ReceivingService(ibOrderRepository, ibLineRepository, lotRepository, locRepository,
                invRepository, invHistRepository, new InvStore(invRepository, invHistRepository),
                prodRepository, inspectionService);

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
        when(ibOrderRepository.findById(10L)).thenReturn(Optional.of(order));

        ibLine = mock(IbLine.class);
        when(ibLine.getId()).thenReturn(100L);
        when(ibLine.getIbOrder()).thenReturn(order);
        when(ibLine.getProd()).thenReturn(prod);
        when(ibLine.getExpctQty()).thenReturn(240L); // 10박스 예정
        when(ibLine.getRcvdQty()).thenReturn(0L);
        when(ibLineRepository.findById(100L)).thenReturn(Optional.of(ibLine));

        staging = mock(Loc.class);
        when(staging.getId()).thenReturn(5L);
        when(locRepository.findByLocCd("RCV-STAGE")).thenReturn(Optional.of(staging));

        lot = mock(Lot.class);
        when(lot.getId()).thenReturn(7L);
        when(lotRepository.findByProdIdAndReceiptDtAndMfgDt(anyLong(), any(), any()))
                .thenReturn(Optional.of(lot));

        inv = mock(Inv.class);
        when(invRepository.findByProdIdAndLocIdAndLotId(1L, 5L, 7L)).thenReturn(Optional.of(inv));
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

    @Test
    @DisplayName("재사용할 배치가 없으면 Lot을 만든다 — 번호는 상품별·입고일자별 건수+1 (미관리 상품은 두 날짜 null)")
    void receive_createsLotWithSequentialNo() {
        when(lotRepository.findByProdIdAndReceiptDtAndMfgDt(anyLong(), any(), any())).thenReturn(Optional.empty());
        when(lotRepository.countByProdIdAndReceiptDt(1L, LocalDate.of(2026, 8, 4))).thenReturn(2L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));
        when(invRepository.findByProdIdAndLocIdAndLotId(any(), any(), any())).thenReturn(Optional.of(inv));

        receivingService.receive(10L, request(5));

        ArgumentCaptor<Lot> captor = ArgumentCaptor.forClass(Lot.class);
        verify(lotRepository).save(captor.capture());
        Lot created = captor.getValue();
        assertEquals("LOT-260804-003", created.getLotNo());
        assertEquals(LocalDate.of(2026, 8, 4), created.getReceiptDt());
        assertNull(created.getMfgDt());
        assertNull(created.getExpiryDt());
    }

    @Test
    @DisplayName("유통기한 미관리 상품은 제조일자를 보내와도 버린다 — 배치 재사용 조회도 Lot도 null이다")
    void receive_discardsMfgDtForNonShelfLifeProd() {
        when(lotRepository.findByProdIdAndReceiptDtAndMfgDt(anyLong(), any(), any())).thenReturn(Optional.empty());
        when(lotRepository.countByProdIdAndReceiptDt(anyLong(), any())).thenReturn(0L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));
        when(invRepository.findByProdIdAndLocIdAndLotId(any(), any(), any())).thenReturn(Optional.of(inv));

        ReceiveRequest.Line line = line(100L, 5);
        line.setMfgDt(LocalDate.of(2026, 8, 1));

        receivingService.receive(10L, request(line));

        verify(lotRepository).findByProdIdAndReceiptDtAndMfgDt(1L, LocalDate.of(2026, 8, 4), null);
        ArgumentCaptor<Lot> captor = ArgumentCaptor.forClass(Lot.class);
        verify(lotRepository).save(captor.capture());
        assertNull(captor.getValue().getMfgDt());
        assertNull(captor.getValue().getExpiryDt());
    }

    @Test
    @DisplayName("유통기한 관리 상품의 Lot은 제조일자 + shelfLifeDays를 유통기한으로 계산해 저장한다")
    void receive_computesExpiryDtFromMfgDt() {
        when(prod.getShelfLifeDays()).thenReturn(10);
        when(lotRepository.findByProdIdAndReceiptDtAndMfgDt(anyLong(), any(), any())).thenReturn(Optional.empty());
        when(lotRepository.countByProdIdAndReceiptDt(anyLong(), any())).thenReturn(0L);
        when(lotRepository.save(any(Lot.class))).thenAnswer(a -> a.getArgument(0));
        when(invRepository.findByProdIdAndLocIdAndLotId(any(), any(), any())).thenReturn(Optional.of(inv));

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
}
