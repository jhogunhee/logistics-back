package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbLine;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.project.omsback.inbound.entity.QOmsIbLine.omsIbLine;
import static com.project.wmsback.master.entity.QProd.prod;

@RequiredArgsConstructor
public class OmsIbLineRepositoryImpl implements OmsIbLineRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OmsIbLine> findAllByOrderIdWithProd(Long omsIbOrderId) {
        return queryFactory
                .selectFrom(omsIbLine)
                .innerJoin(omsIbLine.prod, prod).fetchJoin()
                .where(omsIbLine.omsIbOrder.id.eq(omsIbOrderId))
                .orderBy(omsIbLine.id.asc())
                .fetch();
    }
}
