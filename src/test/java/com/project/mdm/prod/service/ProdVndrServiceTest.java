package com.project.mdm.prod.service;

import com.project.mdm.prod.dto.ProdVndrSaveRequest;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdVndr;
import com.project.mdm.prod.repository.ProdRepository;
import com.project.mdm.prod.repository.ProdVndrRepository;
import com.project.mdm.vendor.entity.Vendor;
import com.project.mdm.vendor.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 상품 거래처 저장의 검증 명세 — 범위 규칙(ck_prod_vndr_qty 선반영) · 짝 중복(uq_prod_vndr 선반영) ·
 * 코드로 들어온 상품·거래처의 존재를 커밋 전에 사용자 메시지로 돌려주는지 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProdVndrServiceTest {

    @Mock ProdVndrRepository prodVndrRepository;
    @Mock ProdRepository prodRepository;
    @Mock VendorRepository vendorRepository;

    private ProdVndrService service;
    private Prod prod;
    private Vendor vendor;

    @BeforeEach
    void setUp() {
        service = new ProdVndrService(prodVndrRepository, prodRepository, vendorRepository);

        prod = mock(Prod.class);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        when(prod.getInbUomCd()).thenReturn("BOX");
        when(prod.eaQtyOf("BOX")).thenReturn(24L);
        vendor = mock(Vendor.class);
        when(vendor.getVndrCd()).thenReturn("VD-0001");

        when(prodRepository.findByProdCd("PROD-0001")).thenReturn(Optional.of(prod));
        when(vendorRepository.findByVndrCd("VD-0001")).thenReturn(Optional.of(vendor));
        when(prodVndrRepository.findByProdAndVendor(any(), any())).thenReturn(Optional.empty());
    }

    private ProdVndrSaveRequest row(String status, Long min, Long max, Long moq, Integer lead, Integer prty) {
        ProdVndrSaveRequest req = new ProdVndrSaveRequest();
        req.setStatus(status);
        req.setProdCd("PROD-0001");
        req.setVndrCd("VD-0001");
        req.setMinQty(min);
        req.setMaxQty(max);
        req.setMinOdrQty(moq);
        req.setLeadDays(lead);
        req.setPrty(prty);
        return req;
    }

    @Test
    @DisplayName("정상 등록 — 비워 보낸 값은 컬럼 DEFAULT와 같은 값으로 채운다")
    void createFillsDefaults() {
        service.saveAll(List.of(row("C", 100L, 500L, null, null, null)));

        ArgumentCaptor<ProdVndr> captor = ArgumentCaptor.forClass(ProdVndr.class);
        verify(prodVndrRepository).save(captor.capture());
        ProdVndr saved = captor.getValue();
        assertEquals(100L, saved.getMinQty());
        assertEquals(500L, saved.getMaxQty());
        assertEquals(1L, saved.getMinOdrQty());
        assertEquals(1, saved.getLeadDays());
        assertEquals(1, saved.getPrty());
    }

    @Test
    @DisplayName("발주점은 발주 상한 이하여야 한다")
    void rejectsMinAboveMax() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", 600L, 500L, 1L, 1, 1))));
        assertTrue(e.getMessage().contains("발주점"));
    }

    @Test
    @DisplayName("발주점 0 이상 · 발주 상한 1 이상 · MOQ 1 이상 · 리드타임 0 이상 · 우선순위 1 이상")
    void rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(List.of(row("C", null, 500L, 1L, 1, 1))));
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(List.of(row("C", -1L, 500L, 1L, 1, 1))));
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(List.of(row("C", 0L, 0L, 1L, 1, 1))));
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(List.of(row("C", 0L, 500L, 0L, 1, 1))));
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(List.of(row("C", 0L, 500L, 1L, -1, 1))));
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(List.of(row("C", 0L, 500L, 1L, 1, 0))));
        verify(prodVndrRepository, never()).save(any());
    }

    @Test
    @DisplayName("입고단위 포장이 없는 상품은 등록을 막는다 — 발주 수량을 낱개로 환산할 수 없어 산정이 그 상품에서 멈춘다")
    void rejectsProductWithoutInboundUom() {
        when(prod.eaQtyOf("BOX")).thenThrow(new IllegalStateException("상품에 등록되지 않은 단위입니다"));

        assertThrows(IllegalStateException.class,
                () -> service.saveAll(List.of(row("C", 100L, 500L, 1L, 1, 1))));
    }

    @Test
    @DisplayName("같은 상품에 같은 거래처를 두 번 등록할 수 없다 (uq_prod_vndr 선반영)")
    void rejectsDuplicatePair() {
        ProdVndr existing = mock(ProdVndr.class);
        when(existing.getId()).thenReturn(99L);
        when(prodVndrRepository.findByProdAndVendor(prod, vendor)).thenReturn(Optional.of(existing));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.saveAll(List.of(row("C", 100L, 500L, 1L, 1, 1))));
        assertTrue(e.getMessage().contains("이미 등록된"));
    }

    @Test
    @DisplayName("수정은 자기 자신과의 중복을 짚지 않는다")
    void updateAllowsOwnPair() {
        ProdVndr self = mock(ProdVndr.class);
        when(self.getId()).thenReturn(7L);
        when(prodVndrRepository.findById(7L)).thenReturn(Optional.of(self));
        when(prodVndrRepository.findByProdAndVendor(prod, vendor)).thenReturn(Optional.of(self));

        ProdVndrSaveRequest req = row("U", 100L, 500L, 2L, 3, 1);
        req.setProdVndrId(7L);
        service.saveAll(List.of(req));

        verify(self).update(prod, vendor, 100L, 500L, 2L, 3, 1);
    }

    @Test
    @DisplayName("존재하지 않는 상품·거래처 코드는 그 자리에서 거부한다")
    void rejectsUnknownCodes() {
        ProdVndrSaveRequest unknownProd = row("C", 100L, 500L, 1L, 1, 1);
        unknownProd.setProdCd("PROD-9999");
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(List.of(unknownProd)));

        ProdVndrSaveRequest unknownVendor = row("C", 100L, 500L, 1L, 1, 1);
        unknownVendor.setVndrCd("VD-9999");
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(List.of(unknownVendor)));

        ProdVndrSaveRequest blankProd = row("C", 100L, 500L, 1L, 1, 1);
        blankProd.setProdCd("  ");
        assertThrows(IllegalArgumentException.class, () -> service.saveAll(List.of(blankProd)));
    }

    @Test
    @DisplayName("삭제는 가드 없이 지운다 — 어떤 문서도 prod_vndr_id를 참조하지 않는 설정 마스터")
    void deleteHasNoGuard() {
        ProdVndr target = mock(ProdVndr.class);
        when(prodVndrRepository.findById(7L)).thenReturn(Optional.of(target));

        ProdVndrSaveRequest req = new ProdVndrSaveRequest();
        req.setStatus("D");
        req.setProdVndrId(7L);
        service.saveAll(List.of(req));

        verify(prodVndrRepository).delete(target);
    }
}
