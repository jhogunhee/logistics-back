package com.project.wmsback.inventory.repository;

import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbStatus;
import com.project.wmsback.inventory.service.ProdStockPort;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.project.wmsback.inbound.entity.QIbLine.ibLine;
import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.repository.InvQueryExpressions.avalQty;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QZon.zon;

/**
 * 상품별 재고 현황 조회 — JPAQueryFactory만 드는 읽기 전용 조회 포트
 * (SpmtQueryRepository와 같은 형태, 3파일 삼각형 아님).
 * <p>
 * 쿼리를 둘로 나눈 이유는 모집단이 다르기 때문이다 — 하나는 재고 행, 하나는 입고예정 라인이라
 * 한 쿼리로 붙이면 join 곱셈이 난다(같은 상품의 재고 3행 × 예정 2행 = 6행).
 */
@Repository
@RequiredArgsConstructor
public class ProdStockQueryRepository implements ProdStockPort {

    private final JPAQueryFactory queryFactory;

    @Override
    public Map<Long, ProdStock> stockByProd(Collection<Long> prodIds) {
        if (prodIds == null || prodIds.isEmpty()) {
            return Map.of();
        }

        NumberExpression<Long> aval = avalQty().sum();
        List<Tuple> avalRows = queryFactory
                .select(inv.prod.id, aval)
                .from(inv)
                .join(inv.loc, loc)
                .join(loc.zon, zon)
                .where(
                        inv.prod.id.in(prodIds),
                        // 반품존은 뺀다 — 불량으로 받아 보류에 묶인 물건이라 할당 후보도 아니다
                        zon.bizDvsn.ne(BizDvsn.RTNGS)
                )
                .groupBy(inv.prod.id)
                .fetch();

        NumberExpression<Long> openAsn = ibLine.expctQty.subtract(ibLine.rcvdQty).subtract(ibLine.rjctQty).sum();
        List<Tuple> asnRows = queryFactory
                .select(ibLine.prod.id, openAsn)
                .from(ibLine)
                .join(ibLine.ibOrder, ibOrder)
                .where(
                        ibLine.prod.id.in(prodIds),
                        // 입고확정된 건은 결품이 못박혀 잔량이 「오고 있는 것」이 아니다
                        ibOrder.status.ne(IbStatus.CONFIRMED),
                        ibOrder.odrDvsn.ne(IbOrder.RTNGS)
                )
                .groupBy(ibLine.prod.id)
                .fetch();

        Map<Long, long[]> byProd = new LinkedHashMap<>(); // [가용, 미입고 예정]
        for (Tuple row : avalRows) {
            byProd.computeIfAbsent(row.get(inv.prod.id), id -> new long[2])[0] = orZero(row.get(aval));
        }
        for (Tuple row : asnRows) {
            byProd.computeIfAbsent(row.get(ibLine.prod.id), id -> new long[2])[1] = orZero(row.get(openAsn));
        }

        Map<Long, ProdStock> result = new LinkedHashMap<>();
        byProd.forEach((prodId, qty) -> result.put(prodId, new ProdStock(prodId, qty[0], qty[1])));
        return result;
    }

    private static long orZero(Long value) {
        return value != null ? value : 0L;
    }
}
