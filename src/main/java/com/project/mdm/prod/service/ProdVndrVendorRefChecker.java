package com.project.mdm.prod.service;

import com.project.mdm.prod.repository.ProdVndrRepository;
import com.project.mdm.vendor.service.VendorRefChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 상품 거래처 마스터가 벤더를 참조 중인지 벤더 마스터에 알려주는 구현체 ({@link VendorRefChecker} 참고).
 * 상품 쪽 짝은 {@link ProdVndrProdRefChecker} — 나뉜 이유는 그쪽 주석에 적었다.
 */
@Component
@Order(4)
@RequiredArgsConstructor
public class ProdVndrVendorRefChecker implements VendorRefChecker {

    private final ProdVndrRepository prodVndrRepository;

    @Override
    public String findReference(Long vendorId) {
        return prodVndrRepository.existsByVendorId(vendorId) ? "상품 거래처(발주 기준)" : null;
    }
}
