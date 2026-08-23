package com.project.wmsback.inventory.repository;

import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.service.ProdLocKey;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.project.wmsback.inbound.entity.QPutawayTask.putawayTask;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.entity.QInvMovTask.invMovTask;

/**
 * 적재가능수량 계산에 필요한 조회 — 현재고와 미완료 지시 유입 잔량.
 * <p>
 * {@code 적재가능 = max_qty − 현재고 − 유입 잔량}의 두 항을 한 클래스에 모은다. 유입을 만드는 지시가
 * 이동지시(inv_mov_task)와 적치지시(putaway_task) 둘이라 <b>여기서만</b> 합산한다 — 두 테이블을
 * 각자 조회해 서비스에서 더하면 새 지시 유형이 생길 때 합산을 빠뜨리기 쉽다.
 * (JPA는 서로 다른 엔티티의 UNION을 지원하지 않아 쿼리 두 번 + 병합이 최선이다.)
 * <p>
 * Spring Data 인터페이스 없이 {@code JPAQueryFactory}만 드는 읽기 전용 조회 포트다 —
 * 저장이 없고 동적 조건도 없어 3파일 삼각형을 만들 이유가 없다.
 */
@Repository
@RequiredArgsConstructor
public class LocCapacityQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** 이 로케이션이 지금 안고 있는 실물 수량 (전 상품 합) */
    public long onHandQty(Long locId) {
        return sumOf(queryFactory
                .select(inv.onHandQty.sum())
                .from(inv)
                .where(inv.loc.id.eq(locId))
                .fetchOne());
    }

    /** 이 로케이션으로 들어올 미완료 잔량 (이동지시 + 적치지시) */
    public long openInflowQty(Long locId) {
        return sumOf(queryFactory
                .select(invMovTask.drctQty.subtract(invMovTask.cmplQty).sum())
                .from(invMovTask)
                .where(invMovTask.toLoc.id.eq(locId), invMovTask.status.eq(InvMovStatus.DIRECTED))
                .fetchOne())
                + sumOf(queryFactory
                .select(putawayTask.drctQty.subtract(putawayTask.cmplQty).sum())
                .from(putawayTask)
                .where(putawayTask.toLoc.id.eq(locId), putawayTask.status.eq(PutawayTaskStatus.DIRECTED))
                .fetchOne());
    }

    /**
     * 로케이션별 미완료 유입 잔량 (잔량이 있는 로케이션만).
     * 추천이 후보 수십 개의 용량을 한 번에 계산할 때 쓴다 — 후보마다 조회하면 그대로 N+1이다.
     */
    public Map<Long, Long> openInflowQtyByLoc() {
        Map<Long, Long> byLoc = new HashMap<>();

        NumberExpression<Long> movRemainder = invMovTask.drctQty.subtract(invMovTask.cmplQty).sum();
        merge(byLoc, queryFactory
                .select(invMovTask.toLoc.id, movRemainder)
                .from(invMovTask)
                .where(invMovTask.status.eq(InvMovStatus.DIRECTED))
                .groupBy(invMovTask.toLoc.id)
                .fetch(), invMovTask.toLoc.id, movRemainder);

        NumberExpression<Long> ptawyRemainder = putawayTask.drctQty.subtract(putawayTask.cmplQty).sum();
        merge(byLoc, queryFactory
                .select(putawayTask.toLoc.id, ptawyRemainder)
                .from(putawayTask)
                .where(putawayTask.status.eq(PutawayTaskStatus.DIRECTED))
                .groupBy(putawayTask.toLoc.id)
                .fetch(), putawayTask.toLoc.id, ptawyRemainder);

        return byLoc;
    }

    private void merge(Map<Long, Long> target, List<Tuple> rows,
                       Expression<Long> locIdPath, Expression<Long> qtyPath) {
        for (Tuple row : rows) {
            target.merge(row.get(locIdPath), sumOf(row.get(qtyPath)), Long::sum);
        }
    }

    /**
     * 특정 상품이 특정 로케이션으로 들어올 미완료 잔량. 보충의 부족량 재검증용 —
     * 전용 자리로 오는 타상품 지시는 물리 용량은 차지해도 그 상품의 보충 판정과는 무관하다.
     * 적치지시는 상품 컬럼이 없어 Lot을 거쳐 상품을 본다.
     */
    public long openInflowQty(Long prodId, Long locId) {
        return sumOf(queryFactory
                .select(invMovTask.drctQty.subtract(invMovTask.cmplQty).sum())
                .from(invMovTask)
                .where(invMovTask.toLoc.id.eq(locId), invMovTask.prod.id.eq(prodId),
                        invMovTask.status.eq(InvMovStatus.DIRECTED))
                .fetchOne())
                + sumOf(queryFactory
                .select(putawayTask.drctQty.subtract(putawayTask.cmplQty).sum())
                .from(putawayTask)
                .where(putawayTask.toLoc.id.eq(locId), putawayTask.lot.prod.id.eq(prodId),
                        putawayTask.status.eq(PutawayTaskStatus.DIRECTED))
                .fetchOne());
    }

    /** 상품×로케이션별 미완료 유입 잔량 (잔량이 있는 조합만). 보충 산정이 대상 전체를 한 번에 판정할 때 쓴다 */
    public Map<ProdLocKey, Long> openInflowQtyByProdLoc() {
        Map<ProdLocKey, Long> byProdLoc = new HashMap<>();

        NumberExpression<Long> movRemainder = invMovTask.drctQty.subtract(invMovTask.cmplQty).sum();
        for (Tuple row : queryFactory
                .select(invMovTask.prod.id, invMovTask.toLoc.id, movRemainder)
                .from(invMovTask)
                .where(invMovTask.status.eq(InvMovStatus.DIRECTED))
                .groupBy(invMovTask.prod.id, invMovTask.toLoc.id)
                .fetch()) {
            byProdLoc.merge(new ProdLocKey(row.get(invMovTask.prod.id), row.get(invMovTask.toLoc.id)),
                    sumOf(row.get(movRemainder)), Long::sum);
        }

        NumberExpression<Long> ptawyRemainder = putawayTask.drctQty.subtract(putawayTask.cmplQty).sum();
        for (Tuple row : queryFactory
                .select(putawayTask.lot.prod.id, putawayTask.toLoc.id, ptawyRemainder)
                .from(putawayTask)
                .where(putawayTask.status.eq(PutawayTaskStatus.DIRECTED))
                .groupBy(putawayTask.lot.prod.id, putawayTask.toLoc.id)
                .fetch()) {
            byProdLoc.merge(new ProdLocKey(row.get(putawayTask.lot.prod.id), row.get(putawayTask.toLoc.id)),
                    sumOf(row.get(ptawyRemainder)), Long::sum);
        }

        return byProdLoc;
    }

    /** 대상 행이 없으면 SUM이 null이다 — 0으로 본다 */
    private long sumOf(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }
}
