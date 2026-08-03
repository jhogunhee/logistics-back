package com.project.omsback.outbound.service;

import com.project.mdm.prod.service.ProdRefChecker;
import com.project.omsback.outbound.repository.OmsOutbLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * OMS 출고주문이 상품을 참조 중인지 상품 마스터에 알려주는 구현체 ({@link ProdRefChecker} 참고).
 */
@Component
@Order(3)
@RequiredArgsConstructor
public class OmsOutbProdRefChecker implements ProdRefChecker {

    private final OmsOutbLineRepository omsOutbLineRepository;

    @Override
    public String findReference(Long prodId) {
        return omsOutbLineRepository.existsByProdId(prodId) ? "출고주문(OMS)" : null;
    }
}
