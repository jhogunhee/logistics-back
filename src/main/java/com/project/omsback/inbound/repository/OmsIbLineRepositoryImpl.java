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
        // 응답이 환산수량(Prod.toOutbQty → prod.uoms)까지 쓰므로 포장 컬렉션도 함께 로딩한다.
        // 컬렉션 fetch join은 행을 곱하므로 distinct로 라인 중복을 걷어낸다.
        return queryFactory
                .selectFrom(omsIbLine).distinct()
                .innerJoin(omsIbLine.prod, prod).fetchJoin()
                .leftJoin(prod.uoms).fetchJoin()
                .where(omsIbLine.omsIbOrder.id.eq(omsIbOrderId))
                .orderBy(omsIbLine.id.asc())
                .fetch();
    }
}
