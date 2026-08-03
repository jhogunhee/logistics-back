package com.project.omsback.outbound.repository;

import com.project.omsback.outbound.entity.OmsOutbLine;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.omsback.outbound.entity.QOmsOutbLine.omsOutbLine;

@RequiredArgsConstructor
public class OmsOutbLineRepositoryImpl implements OmsOutbLineRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OmsOutbLine> findAllByOrderIdWithProd(Long omsOutbOrderId) {
        // 응답이 쓰는 값은 전부 상품 스칼라라 포장 컬렉션(prod.uoms)까지 당길 필요가 없다 —
        // 출고 수량은 환산하지 않기 때문이다 (입고주문 라인 조회와 다른 점).
        return queryFactory
                .selectFrom(omsOutbLine)
                .innerJoin(omsOutbLine.prod, prod).fetchJoin()
                .where(omsOutbLine.omsOutbOrder.id.eq(omsOutbOrderId))
                .orderBy(omsOutbLine.id.asc())
                .fetch();
    }
}
