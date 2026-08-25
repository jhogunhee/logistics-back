package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbLine;
import com.project.omsback.inbound.entity.OmsIbOrder;
import com.project.omsback.inbound.entity.OmsIbStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.project.omsback.inbound.entity.QOmsIbLine.omsIbLine;
import static com.project.omsback.inbound.entity.QOmsIbOrder.omsIbOrder;
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

    @Override
    public Map<Long, Long> openOdrQtyByProd(Collection<Long> prodIds) {
        if (prodIds == null || prodIds.isEmpty()) {
            return Map.of();
        }
        NumberExpression<Long> openQty = omsIbLine.odrQty.sum();
        List<Tuple> rows = queryFactory
                .select(omsIbLine.prod.id, openQty)
                .from(omsIbLine)
                .join(omsIbLine.omsIbOrder, omsIbOrder)
                .where(
                        omsIbLine.prod.id.in(prodIds),
                        // 확정된 발주는 ASN이 되어 입고예정 쪽에서 세므로 여기서 또 세면 두 번이다
                        omsIbOrder.status.eq(OmsIbStatus.CREATED),
                        omsIbOrder.odrDvsn.ne(OmsIbOrder.RTNGS)
                )
                .groupBy(omsIbLine.prod.id)
                .fetch();

        Map<Long, Long> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            Long qty = row.get(openQty);
            result.put(row.get(omsIbLine.prod.id), qty != null ? qty : 0L);
        }
        return result;
    }
}
