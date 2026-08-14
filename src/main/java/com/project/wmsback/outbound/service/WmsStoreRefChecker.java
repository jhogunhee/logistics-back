package com.project.wmsback.outbound.service;

import com.project.mdm.store.service.StoreRefChecker;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.project.wmsback.outbound.entity.QOutbOrder.outbOrder;

/**
 * WMS 출고주문이 점포를 참조 중인지 점포 마스터에 알려주는 구현체 ({@link StoreRefChecker} 참고).
 */
@Component
@Order(1) // OMS 출고주문보다 먼저 — 창고에 작업문서가 있다는 사실이 사용자에게 더 와닿는다
@RequiredArgsConstructor
public class WmsStoreRefChecker implements StoreRefChecker {

    private final JPAQueryFactory queryFactory;

    @Override
    public String findReference(Long storeId) {
        boolean exists = queryFactory.selectOne()
                .from(outbOrder)
                .where(outbOrder.store.id.eq(storeId))
                .fetchFirst() != null;
        return exists ? "출고주문" : null;
    }
}
