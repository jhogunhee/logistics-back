package com.project.omsback.inbound.service;

import com.project.common.batch.BatchExecutor;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdVndr;
import com.project.mdm.prod.repository.ProdVndrRepository;
import com.project.mdm.vendor.entity.Vendor;
import com.project.omsback.inbound.dto.AtoOdrIssueRequest;
import com.project.omsback.inbound.dto.AtoOdrProposalResponse;
import com.project.omsback.inbound.dto.AtoOdrSearchCond;
import com.project.omsback.inbound.dto.OmsIbOrderSaveRequest;
import com.project.omsback.inbound.repository.OmsIbLineRepository;
import com.project.wmsback.inventory.service.ProdStockPort;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 자동발주 산정·발행 — 대표 벤더 선택, 순재고 세 항, 벤더별 묶기, 발행이 입고주문 창구에 싣는 값.
 * 수량 산식 자체는 {@link AtoOdrQtyCalcTest}가 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AtoOdrServiceTest {

    @Mock ProdVndrRepository prodVndrRepository;
    @Mock ProdStockPort prodStockPort;
    @Mock OmsIbLineRepository omsIbLineRepository;
    @Mock OmsIbOrderService omsIbOrderService;
    @Mock BatchExecutor batchExecutor;

    private AtoOdrService service;

    @BeforeEach
    void setUp() {
        service = new AtoOdrService(prodVndrRepository, prodStockPort, omsIbLineRepository,
                omsIbOrderService, batchExecutor);
        when(prodStockPort.stockByProd(anyCollection())).thenReturn(Map.of());
        when(omsIbLineRepository.openOdrQtyByProd(anyCollection())).thenReturn(Map.of());
    }

    // --- 픽스처 -----------------------------------------------------------

    private Prod prod(long id, String cd, String uomCd, long eaPerUom) {
        Prod prod = mock(Prod.class);
        when(prod.getId()).thenReturn(id);
        when(prod.getProdCd()).thenReturn(cd);
        when(prod.getProdNm()).thenReturn(cd + " 상품");
        when(prod.getInbUomCd()).thenReturn(uomCd);
        when(prod.eaQtyOf(uomCd)).thenReturn(eaPerUom);
        when(prod.toEaQty(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(uomCd)))
                .thenAnswer(inv -> inv.<Long>getArgument(0) * eaPerUom);
        return prod;
    }

    private Vendor vendor(long id, String cd) {
        Vendor vendor = mock(Vendor.class);
        when(vendor.getId()).thenReturn(id);
        when(vendor.getVndrCd()).thenReturn(cd);
        when(vendor.getVndrNm()).thenReturn(cd + " 거래처");
        return vendor;
    }

    private ProdVndr prodVndr(long id, Prod prod, Vendor vendor,
                              long min, long max, long moq, int leadDays) {
        ProdVndr pv = mock(ProdVndr.class);
        when(pv.getId()).thenReturn(id);
        when(pv.getProd()).thenReturn(prod);
        when(pv.getVendor()).thenReturn(vendor);
        when(pv.getMinQty()).thenReturn(min);
        when(pv.getMaxQty()).thenReturn(max);
        when(pv.getMinOdrQty()).thenReturn(moq);
        when(pv.getLeadDays()).thenReturn(leadDays);
        return pv;
    }

    // --- 산정 -------------------------------------------------------------

    @Test
    @DisplayName("벤더별로 한 건씩 묶고, 발주점을 채운 상품만 빠진다")
    void groupsShortProductsByVendor() {
        Vendor v1 = vendor(1, "VD-0001");
        Vendor v2 = vendor(2, "VD-0002");
        Prod shortA = prod(10, "PROD-A", "BOX", 24);
        Prod shortB = prod(11, "PROD-B", "EA", 1);
        Prod enough = prod(12, "PROD-C", "BOX", 10);
        // 목 생성은 when() 밖에서 끝낸다 — when(...) 인자 안에서 다른 목을 스터빙하면 중첩 스터빙이 된다
        List<ProdVndr> master = List.of(
                prodVndr(100, shortA, v1, 100, 240, 1, 2),
                prodVndr(101, enough, v1, 50, 200, 1, 1),
                prodVndr(102, shortB, v2, 30, 80, 1, 5));
        when(prodVndrRepository.search(any())).thenReturn(master);
        when(prodStockPort.stockByProd(anyCollection())).thenReturn(Map.of(
                10L, new ProdStockPort.ProdStock(10L, 0, 0),      // 순재고 0 < 100 → 대상
                12L, new ProdStockPort.ProdStock(12L, 60, 0),     // 순재고 60 > 50 → 제외
                11L, new ProdStockPort.ProdStock(11L, 10, 5)      // 순재고 15 < 30 → 대상
        ));

        List<AtoOdrProposalResponse> proposals = service.plan(new AtoOdrSearchCond());

        assertEquals(2, proposals.size());
        AtoOdrProposalResponse first = proposals.get(0);
        assertEquals("VD-0001", first.vndrCd());
        assertEquals(1, first.lines().size());
        assertEquals("PROD-A", first.lines().get(0).prodCd());
        assertEquals(10, first.lines().get(0).odrQty());          // 부족 240 ÷ 24 = 10 BOX
        assertEquals(1, proposals.get(1).lines().size());
        assertEquals(65, proposals.get(1).lines().get(0).odrQty()); // 부족 80 − 15 = 65 EA
    }

    @Test
    @DisplayName("한 상품에 벤더가 여럿이면 조회 순서상 첫 행이 대표다 (정렬이 prty → id)")
    void firstRowWinsAsPrimaryVendor() {
        Vendor cheap = vendor(1, "VD-0001");
        Vendor backup = vendor(2, "VD-0002");
        Prod p = prod(10, "PROD-A", "EA", 1);
        List<ProdVndr> master = List.of(
                prodVndr(100, p, cheap, 100, 200, 1, 2),
                prodVndr(101, p, backup, 100, 500, 1, 9));
        when(prodVndrRepository.search(any())).thenReturn(master);

        List<AtoOdrProposalResponse> proposals = service.plan(new AtoOdrSearchCond());

        assertEquals(1, proposals.size());
        assertEquals("VD-0001", proposals.get(0).vndrCd());
        assertEquals(200, proposals.get(0).lines().get(0).odrQty()); // 뒤 행의 상한(500)이 아니다
    }

    @Test
    @DisplayName("이미 낸 미확정 발주가 순재고에 들어가 같은 상품이 다시 잡히지 않는다 (중복 발주 방지)")
    void openOrdersCountAsIncomingStock() {
        Vendor v = vendor(1, "VD-0001");
        Prod p = prod(10, "PROD-A", "BOX", 24);
        List<ProdVndr> master = List.of(prodVndr(100, p, v, 100, 240, 1, 2));
        when(prodVndrRepository.search(any())).thenReturn(master);
        when(prodStockPort.stockByProd(anyCollection()))
                .thenReturn(Map.of(10L, new ProdStockPort.ProdStock(10L, 0, 0)));
        when(omsIbLineRepository.openOdrQtyByProd(anyCollection()))
                .thenReturn(Map.of(10L, 10L)); // 10 BOX = 240 EA

        assertTrue(service.plan(new AtoOdrSearchCond()).isEmpty());
    }

    @Test
    @DisplayName("입고 예정일은 오늘 + 그 벤더 라인 중 가장 긴 리드타임 (헤더에 예정일이 하나뿐이라)")
    void expctDeUsesLongestLeadOfVendor() {
        Vendor v = vendor(1, "VD-0001");
        List<ProdVndr> master = List.of(
                prodVndr(100, prod(10, "PROD-A", "EA", 1), v, 100, 200, 1, 2),
                prodVndr(101, prod(11, "PROD-B", "EA", 1), v, 100, 200, 1, 7));
        when(prodVndrRepository.search(any())).thenReturn(master);

        AtoOdrProposalResponse proposal = service.plan(new AtoOdrSearchCond()).get(0);

        assertEquals(LocalDate.now().plusDays(7), proposal.expctDe());
    }

    @Test
    @DisplayName("등록된 상품 거래처가 없으면 조회도 하지 않고 빈 결과다")
    void emptyMasterYieldsNoProposal() {
        when(prodVndrRepository.search(any())).thenReturn(List.of());

        assertTrue(service.plan(new AtoOdrSearchCond()).isEmpty());
        verify(prodStockPort, org.mockito.Mockito.never()).stockByProd(anyCollection());
    }

    // --- 발행 -------------------------------------------------------------

    @Test
    @DisplayName("발행은 입고주문 창구에 자동발주 구분·거래처·라인을 그대로 싣는다 (채번·검증은 그쪽 몫)")
    void issueDelegatesToOrderService() {
        when(prodVndrRepository.findProdIdsByVendorId(1L)).thenReturn(Set.of(10L, 11L));

        service.issue(request(1L, LocalDate.of(2026, 8, 27), 10L, 3L));

        ArgumentCaptor<OmsIbOrderSaveRequest> captor = ArgumentCaptor.forClass(OmsIbOrderSaveRequest.class);
        verify(omsIbOrderService).create(captor.capture());
        OmsIbOrderSaveRequest req = captor.getValue();
        assertEquals("ATO", req.getOdrDvsn());
        assertEquals(1L, req.getVendorId());
        assertEquals(LocalDate.of(2026, 8, 27), req.getExpctDe());
        assertEquals(1, req.getLines().size());
        assertEquals(10L, req.getLines().get(0).getProdId());
        assertEquals(3L, req.getLines().get(0).getOdrQty());
        assertTrue(req.getRmk().startsWith("자동발주"));
    }

    @Test
    @DisplayName("그 거래처의 상품 거래처 마스터에 없는 상품은 거부한다 — 산정이 낸 제안이 아니다")
    void rejectsProductNotRegisteredForVendor() {
        when(prodVndrRepository.findProdIdsByVendorId(1L)).thenReturn(Set.of(11L));

        assertThrows(IllegalArgumentException.class,
                () -> service.issue(request(1L, LocalDate.now(), 10L, 3L)));
        verify(omsIbOrderService, org.mockito.Mockito.never()).create(any());
    }

    @Test
    @DisplayName("거래처·예정일·라인·수량은 필수다")
    void rejectsIncompleteRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> service.issue(request(null, LocalDate.now(), 10L, 1L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.issue(request(1L, null, 10L, 1L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.issue(request(1L, LocalDate.now(), 10L, 0L)));

        AtoOdrIssueRequest empty = new AtoOdrIssueRequest();
        empty.setVendorId(1L);
        empty.setExpctDe(LocalDate.now());
        empty.setItems(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.issue(empty));
    }

    private AtoOdrIssueRequest request(Long vendorId, LocalDate expctDe, Long prodId, Long odrQty) {
        AtoOdrIssueRequest.Item item = new AtoOdrIssueRequest.Item();
        item.setProdId(prodId);
        item.setOdrQty(odrQty);
        AtoOdrIssueRequest request = new AtoOdrIssueRequest();
        request.setVendorId(vendorId);
        request.setExpctDe(expctDe);
        request.setItems(List.of(item));
        return request;
    }
}
