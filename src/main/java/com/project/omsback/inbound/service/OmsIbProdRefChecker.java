package com.project.omsback.inbound.service;

import com.project.mdm.prod.service.ProdRefChecker;
import com.project.omsback.inbound.entity.OmsIbStatus;
import com.project.omsback.inbound.repository.OmsIbLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * OMS 입고주문이 상품을 참조 중인지 상품 마스터에 알려주는 구현체 ({@link ProdRefChecker} 참고).
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class OmsIbProdRefChecker implements ProdRefChecker {

    private final OmsIbLineRepository omsIbLineRepository;

    @Override
    public String findReference(Long prodId) {
        return omsIbLineRepository.existsByProdId(prodId) ? "입고주문" : null;
    }

    /** 발주 수량은 입고단위 기준으로 저장돼 있고 확정 시점에 낱개(EA)로 환산된다 */
    @Override
    public String findOpenInbRef(Long prodId) {
        return omsIbLineRepository.existsByProdIdAndOmsIbOrderStatus(prodId, OmsIbStatus.CREATED)
                ? "미확정 입고주문" : null;
    }
}
