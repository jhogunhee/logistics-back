package com.project.omsback.inbound.service;

import com.project.mdm.vendor.service.VendorRefChecker;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.project.omsback.inbound.entity.QOmsIbOrder.omsIbOrder;

/**
 * OMS 입고주문이 벤더를 참조 중인지 벤더 마스터에 알려주는 구현체 ({@link VendorRefChecker} 참고).
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class OmsIbVendorRefChecker implements VendorRefChecker {

    private final JPAQueryFactory queryFactory;

    @Override
    public String findReference(Long vendorId) {
        boolean exists = queryFactory.selectOne()
                .from(omsIbOrder)
                .where(omsIbOrder.vendor.id.eq(vendorId))
                .fetchFirst() != null;
        return exists ? "입고주문(OMS)" : null;
    }
}
