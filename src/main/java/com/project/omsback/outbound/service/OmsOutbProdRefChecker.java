package com.project.omsback.outbound.service;

import com.project.mdm.prod.service.ProdRefChecker;
import com.project.omsback.outbound.entity.OmsOutbStatus;
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

    /**
     * 주문 수량은 출고단위 기준으로 저장돼 있고 확정 시점에 낱개(EA)로 환산된다 —
     * 확정된 뒤의 창고 출고주문은 EA로 못박혀 있어 검사하지 않는다.
     */
    @Override
    public String findOpenOutbRef(Long prodId) {
        return omsOutbLineRepository.existsByProdIdAndOmsOutbOrderStatus(prodId, OmsOutbStatus.CREATED)
                ? "미확정 출고주문" : null;
    }
}
