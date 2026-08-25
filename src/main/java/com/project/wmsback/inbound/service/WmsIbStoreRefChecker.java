package com.project.wmsback.inbound.service;

import com.project.mdm.store.service.StoreRefChecker;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;

/** 반품 입고예정(ASN)이 점포를 참조 중인지 점포 마스터에 알려주는 구현체 ({@link StoreRefChecker} 참고). */
@Component
@Order(3)
@RequiredArgsConstructor
public class WmsIbStoreRefChecker implements StoreRefChecker {

    private final JPAQueryFactory queryFactory;

    @Override
    public String findReference(Long storeId) {
        boolean exists = queryFactory.selectOne()
                .from(ibOrder)
                .where(ibOrder.store.id.eq(storeId))
                .fetchFirst() != null;
        return exists ? "반품 입고예정(ASN)" : null;
    }
}
