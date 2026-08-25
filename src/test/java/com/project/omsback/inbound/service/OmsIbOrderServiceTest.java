package com.project.omsback.inbound.service;

import com.project.common.batch.BatchExecutor;
import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.mdm.store.entity.Store;
import com.project.mdm.store.repository.StoreRepository;
import com.project.mdm.vendor.entity.Vendor;
import com.project.mdm.vendor.repository.VendorRepository;
import com.project.omsback.inbound.dto.OmsIbLineSaveRequest;
import com.project.omsback.inbound.dto.OmsIbOrderSaveRequest;
import com.project.omsback.inbound.entity.OmsIbOrder;
import com.project.omsback.inbound.repository.OmsIbLineRepository;
import com.project.omsback.inbound.repository.OmsIbOrderRepository;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbOrderRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 반품입고 주문 — 상대가 점포이고, 라인엔 사유가 붙고, 확정 환산은 출고단위다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OmsIbOrderServiceTest {

    @Mock OmsIbOrderRepository omsIbOrderRepository;
    @Mock OmsIbLineRepository omsIbLineRepository;
    @Mock ProdRepository prodRepository;
    @Mock VendorRepository vendorRepository;
    @Mock StoreRepository storeRepository;
    @Mock CodeDetailRepository codeDetailRepository;
    @Mock IbOrderRepository ibOrderRepository;
    @Mock NbrService nbrService;
    @Mock BatchExecutor batchExecutor;

    private OmsIbOrderService service;
    private Prod prod;

    @BeforeEach
    void setUp() {
        service = new OmsIbOrderService(omsIbOrderRepository, omsIbLineRepository, prodRepository, vendorRepository,
                storeRepository, codeDetailRepository, ibOrderRepository, nbrService, batchExecutor);
        prod = mock(Prod.class);
        when(prod.getInbUomCd()).thenReturn("BOX");
        when(prod.getOutbUomCd()).thenReturn("EA");
        when(prod.toEaQty(anyLong(), eq("BOX"))).thenAnswer(a -> a.getArgument(0, Long.class) * 24);
        when(prod.toEaQty(anyLong(), eq("EA"))).thenAnswer(a -> a.getArgument(0, Long.class));
        when(prodRepository.findById(1L)).thenReturn(Optional.of(prod));
        when(storeRepository.findById(5L)).thenReturn(Optional.of(mock(Store.class)));
        when(vendorRepository.findById(3L)).thenReturn(Optional.of(mock(Vendor.class)));
        when(codeDetailRepository.existsById(any())).thenReturn(true);
        when(nbrService.issue(any(), any())).thenReturn("NO-1");
    }

    private OmsIbOrderSaveRequest rtngsReq(String rsnCd, String rsnDscr) {
        OmsIbOrderSaveRequest req = new OmsIbOrderSaveRequest();
        req.setOdrDvsn("RTNGS");
        req.setStoreId(5L);
        req.setRefOutbNo("OB-20260820-001");
        req.setExpctDe(LocalDate.of(2026, 8, 26));
        OmsIbLineSaveRequest line = new OmsIbLineSaveRequest();
        line.setProdId(1L);
        line.setOdrQty(10L);
        line.setRsnCd(rsnCd);
        line.setRsnDscr(rsnDscr);
        req.setLines(List.of(line));
        return req;
    }

    @Test
    @DisplayName("반품 등록 — 점포·원 출고번호·라인 사유가 저장된다")
    void createRtngs() {
        service.create(rtngsReq("DAMG", null));

        ArgumentCaptor<OmsIbOrder> captor = ArgumentCaptor.forClass(OmsIbOrder.class);
        verify(omsIbOrderRepository).save(captor.capture());
        OmsIbOrder saved = captor.getValue();
        assertTrue(saved.isRtngs());
        assertNotNull(saved.getStore());
        assertNull(saved.getVendor());
        assertEquals("OB-20260820-001", saved.getRefOutbNo());
        assertEquals("DAMG", saved.getLines().get(0).getRsnCd());
    }

    @Test
    @DisplayName("반품 라인은 사유 필수, ETC면 상세 필수")
    void rtngsLineRequiresRsn() {
        assertThrows(IllegalArgumentException.class, () -> service.create(rtngsReq(null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(rtngsReq("ETC", " ")));
    }

    @Test
    @DisplayName("반품인데 점포가 없으면 거부, 정상인데 벤더가 없으면 거부")
    void partnerRequired() {
        OmsIbOrderSaveRequest noStore = rtngsReq("DAMG", null);
        noStore.setStoreId(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(noStore));

        OmsIbOrderSaveRequest normal = rtngsReq(null, null);
        normal.setOdrDvsn("NRML");
        normal.setStoreId(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(normal));
    }

    @Test
    @DisplayName("정상 라인에 반품사유가 오면 거부")
    void normalLineRejectsRsn() {
        OmsIbOrderSaveRequest normal = rtngsReq("DAMG", null);
        normal.setOdrDvsn("NRML");
        normal.setStoreId(null);
        normal.setVendorId(3L);
        assertThrows(IllegalArgumentException.class, () -> service.create(normal));
    }

    @Test
    @DisplayName("반품 확정 — ASN의 상대는 점포, 구분 RTNGS, 예정수량은 출고단위 환산")
    void confirmRtngsBuildsStoreAsn() {
        OmsIbOrder order = OmsIbOrder.builder().omsIbNo("PO-1").store(mock(Store.class)).odrDvsn("RTNGS")
                .expctDe(LocalDate.of(2026, 8, 26)).build();
        order.addLines(List.of(com.project.omsback.inbound.entity.OmsIbLine.builder().prod(prod).odrQty(10L).rsnCd("DAMG").build()));
        when(omsIbOrderRepository.findById(7L)).thenReturn(Optional.of(order));

        service.confirm(7L);

        ArgumentCaptor<IbOrder> captor = ArgumentCaptor.forClass(IbOrder.class);
        verify(ibOrderRepository).save(captor.capture());
        IbOrder asn = captor.getValue();
        assertTrue(asn.isRtngs());
        assertNotNull(asn.getStore());
        assertEquals(10L, asn.getLines().get(0).getExpctQty());   // EA ×1 — 출고단위 환산
    }
}
