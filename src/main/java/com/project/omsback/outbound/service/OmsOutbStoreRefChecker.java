package com.project.omsback.outbound.service;

import com.project.mdm.store.service.StoreRefChecker;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.project.omsback.outbound.entity.QOmsOutbOrder.omsOutbOrder;

/**
 * OMS 출고주문이 점포를 참조 중인지 점포 마스터에 알려주는 구현체 ({@link StoreRefChecker} 참고).
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class OmsOutbStoreRefChecker implements StoreRefChecker {

    private final JPAQueryFactory queryFactory;

    @Override
    public String findReference(Long storeId) {
        boolean exists = queryFactory.selectOne()
                .from(omsOutbOrder)
                .where(omsOutbOrder.store.id.eq(storeId))
                .fetchFirst() != null;
        return exists ? "출고주문(OMS)" : null;
    }
}
