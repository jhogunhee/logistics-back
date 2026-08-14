package com.project.wmsback.inbound.service;

import com.project.mdm.vendor.service.VendorRefChecker;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;

/**
 * 입고예정(ASN)이 벤더를 참조 중인지 벤더 마스터에 알려주는 구현체 ({@link VendorRefChecker} 참고).
 */
@Component
@Order(1) // OMS 입고주문보다 먼저 — 창고에 작업문서가 있다는 사실이 사용자에게 더 와닿는다
@RequiredArgsConstructor
public class WmsIbVendorRefChecker implements VendorRefChecker {

    private final JPAQueryFactory queryFactory;

    @Override
    public String findReference(Long vendorId) {
        boolean exists = queryFactory.selectOne()
                .from(ibOrder)
                .where(ibOrder.vendor.id.eq(vendorId))
                .fetchFirst() != null;
        return exists ? "입고예정(ASN)" : null;
    }
}
