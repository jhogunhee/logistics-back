package com.project.mdm.prod.service;

import com.project.mdm.prod.repository.ProdVndrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 상품 거래처 마스터가 상품을 참조 중인지 상품 마스터에 알려주는 구현체 ({@link ProdRefChecker} 참고).
 * <p>
 * 벤더 쪽 짝은 {@code ProdVndrVendorRefChecker}다 — 두 포트의 메서드 시그니처가 같아
 * (둘 다 {@code String findReference(Long)}) 한 클래스로는 구현할 수 없다.
 * <p>
 * {@code @Order}가 뒤인 것은 재고·문서 참조("재고" · "입고주문")가 사용자에게 먼저 와닿기 때문이다 —
 * 이 마스터는 지우고 다시 등록하면 그만이라 마지막에 안내하는 편이 낫다.
 */
@Component
@Order(4)
@RequiredArgsConstructor
public class ProdVndrProdRefChecker implements ProdRefChecker {

    private final ProdVndrRepository prodVndrRepository;

    @Override
    public String findReference(Long prodId) {
        return prodVndrRepository.existsByProdId(prodId) ? "상품 거래처(발주 기준)" : null;
    }
}
