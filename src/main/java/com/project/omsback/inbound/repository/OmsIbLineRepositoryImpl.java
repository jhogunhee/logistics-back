package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbLine;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.project.omsback.inbound.entity.QOmsIbLine.omsIbLine;
import static com.project.mdm.prod.entity.QProd.prod;

@RequiredArgsConstructor
public class OmsIbLineRepositoryImpl implements OmsIbLineRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OmsIbLine> findAllByOrderIdWithProd(Long omsIbOrderId) {
        // 응답이 환산수량(Prod.eaQtyOf → prod.uoms)을 쓰지만 포장 컬렉션은 fetch join하지 않는다 —
        // 컬렉션을 붙이면 라인 하나가 포장 수만큼 행으로 불어난다. 지연로딩은
        // default_batch_fetch_size(100)가 IN 한 방으로 묶으므로 쿼리만 하나 더 든다.
        return queryFactory
                .selectFrom(omsIbLine)
                .innerJoin(omsIbLine.prod, prod).fetchJoin()
                .where(omsIbLine.omsIbOrder.id.eq(omsIbOrderId))
                .orderBy(omsIbLine.id.asc())
                .fetch();
    }
}
