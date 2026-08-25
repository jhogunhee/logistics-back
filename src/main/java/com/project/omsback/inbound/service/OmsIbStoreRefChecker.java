package com.project.omsback.inbound.service;

import com.project.mdm.store.service.StoreRefChecker;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.project.omsback.inbound.entity.QOmsIbOrder.omsIbOrder;

/** 반품 입고주문(OMS)이 점포를 참조 중인지 점포 마스터에 알려주는 구현체 ({@link StoreRefChecker} 참고). */
@Component
@Order(4)
@RequiredArgsConstructor
public class OmsIbStoreRefChecker implements StoreRefChecker {

    private final JPAQueryFactory queryFactory;

    @Override
    public String findReference(Long storeId) {
        boolean exists = queryFactory.selectOne()
                .from(omsIbOrder)
                .where(omsIbOrder.store.id.eq(storeId))
                .fetchFirst() != null;
        return exists ? "반품 입고주문(OMS)" : null;
    }
}
