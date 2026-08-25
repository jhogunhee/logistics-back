package com.project.wmsback.strategy.putaway.repository;

import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.strategy.putaway.component.PutawayMethodContext;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.warehouse.entity.QFxngLoc.fxngLoc;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QZon.zon;

/** 적치 추천의 재고 현황 조회. 불변 전제(상품 온도대 일치 + STORAGE)가 여기서 강제된다 */
@Repository
@RequiredArgsConstructor
public class PutawayQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 온도대 일치 보관 로케이션 전체의 (점유수량 합, 해당 상품 보유 여부, 존 업무유형, 고정 여부).
     * 점유 = 그 로케이션의 전체 상품 on_hand 합 — <b>현재고만</b>이다. 미완료 지시가 잡아둔 자리는
     * 여기서 빼지 않고 PutawayRecommendService가 LocCapacityService로 받아 합산한다
     * (유입 원천이 적치지시·이동지시 둘이라 한 쿼리로 묶으면 이 조회 포트가 두 도메인을 알게 된다).
     * 존 조인은 업무유형 조건(BIZ_DVSN) 판정용 — loc.zon_id는 FK가 없어 left join (미등록 존이면 null).
     * 고정 조인은 uq_fxng_loc(로케이션당 ≤1행) 덕에 inv 합계를 곱셈 오염시키지 않는다.
     */
    public List<PutawayMethodContext.LocStock> storageStocks(TmpZon tmpZon, Long prodId) {
        NumberExpression<Long> occupied = inv.onHandQty.sum().coalesce(0L);
        NumberExpression<Long> prodQty = new CaseBuilder()
                .when(inv.prod.id.eq(prodId)).then(inv.onHandQty)
                .otherwise(0L)
                .sum().coalesce(0L);
        NumberExpression<Long> fxngId = fxngLoc.id.max();

        List<Tuple> rows = queryFactory
                .select(loc, occupied, prodQty, zon.bizDvsn, fxngId)
                .from(loc)
                .leftJoin(loc.zon, zon)
                .leftJoin(inv).on(inv.loc.eq(loc))
                .leftJoin(fxngLoc).on(fxngLoc.loc.eq(loc).and(fxngLoc.prod.id.eq(prodId)))
                .where(
                        loc.locTyp.eq(LocTyp.STORAGE),
                        loc.tmpZon.eq(tmpZon),
                        // 반품존은 적치 후보가 아니다 — RtngsLocResolver.inRtngsZon 참고
                        zon.bizDvsn.ne(BizDvsn.RTNGS).or(zon.bizDvsn.isNull())
                )
                .groupBy(loc, zon.bizDvsn)
                .fetch();

        return rows.stream()
                .map(row -> new PutawayMethodContext.LocStock(
                        Objects.requireNonNull(row.get(loc)),
                        Objects.requireNonNullElse(row.get(occupied), 0L),
                        Objects.requireNonNullElse(row.get(prodQty), 0L) > 0,
                        row.get(zon.bizDvsn) != null ? row.get(zon.bizDvsn).name() : null,
                        row.get(fxngId) != null))
                .toList();
    }
}
