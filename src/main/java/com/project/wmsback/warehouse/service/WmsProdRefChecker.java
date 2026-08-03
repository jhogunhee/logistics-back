package com.project.wmsback.warehouse.service;

import com.project.mdm.prod.service.ProdRefChecker;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.project.wmsback.inbound.entity.QIbLine.ibLine;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.outbound.entity.QOutbLine.outbLine;
import static com.project.wmsback.warehouse.entity.QLot.lot;

/**
 * WMS가 상품을 참조 중인지 상품 마스터에 알려주는 구현체 ({@link ProdRefChecker} 참고).
 * <p>
 * 재고 · 이력 · 입고예정 · 출고주문 · Lot 다섯 도메인에 걸쳐 있어 어느 한 도메인의 것이 아니다.
 * Lot을 소유한 `warehouse`에 두고 나머지는 조회만 한다.
 * <p>
 * 참조가 하나라도 나오면 그 자리에서 이름을 돌려준다 — 몇 건인지는 필요 없고, 삭제를 막을
 * 이유 하나면 충분하다. 순서는 사용자가 납득하기 쉬운 쪽부터다(재고 → 이력 → 문서 → Lot).
 */
@Component
@Order(1) // OMS 입고주문보다 먼저 — 창고에 실물이 있다는 사실이 사용자에게 더 와닿는다
@RequiredArgsConstructor
public class WmsProdRefChecker implements ProdRefChecker {

    private final JPAQueryFactory queryFactory;

    @Override
    public String findReference(Long prodId) {
        if (exists(inv.prod.id.eq(prodId), inv)) return "재고";
        if (exists(invHist.prod.id.eq(prodId), invHist)) return "재고 이력";
        if (exists(ibLine.prod.id.eq(prodId), ibLine)) return "입고예정(ASN)";
        if (exists(outbLine.prod.id.eq(prodId), outbLine)) return "출고주문";
        if (exists(lot.prod.id.eq(prodId), lot)) return "Lot";
        return null;
    }

    private boolean exists(BooleanExpression where, EntityPath<?> from) {
        return queryFactory.selectOne().from(from).where(where).fetchFirst() != null;
    }
}
