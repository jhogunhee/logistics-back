package com.project.wmsback.strategy.putaway.repository;

import com.project.wmsback.warehouse.entity.LocType;
import com.project.mdm.prod.entity.TempZone;
import com.project.wmsback.strategy.putaway.method.PutawayMethodContext;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QZon.zon;

/** 적치 추천의 재고 현황 조회. 불변 전제(상품 온도대 일치 + STORAGE)가 여기서 강제된다 */
@Repository
@RequiredArgsConstructor
public class PutawayQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 온도대 일치 보관 로케이션 전체의 (점유수량 합, 해당 상품 보유 여부, 존 업무유형).
     * 점유 = 그 로케이션의 전체 상품 on_hand 합 — putaway_task 지시가 없는 현행 구조라
     * 미완료 지시 잔량은 계산에 없다 (docs/st/전략_프로세스정의서.md §3.1).
     * 존 조인은 업무유형 조건(BIZ_DVSN) 판정용 — loc.zon_cd는 FK가 없어 left join (미등록 존이면 null).
     */
    public List<PutawayMethodContext.LocStock> storageStocks(TempZone tmpZon, Long prodId) {
        NumberExpression<Long> occupied = inv.onHandQty.sum().coalesce(0L);
        NumberExpression<Long> prodQty = new CaseBuilder()
                .when(inv.prod.id.eq(prodId)).then(inv.onHandQty)
                .otherwise(0L)
                .sum().coalesce(0L);

        List<Tuple> rows = queryFactory
                .select(loc, occupied, prodQty, zon.bizDvsn)
                .from(loc)
                .leftJoin(zon).on(zon.zonCd.eq(loc.zonCd))
                .leftJoin(inv).on(inv.loc.eq(loc))
                .where(
                        loc.locTyp.eq(LocType.STORAGE),
                        loc.tmpZon.eq(tmpZon)
                )
                .groupBy(loc, zon.bizDvsn)
                .fetch();

        return rows.stream()
                .map(row -> new PutawayMethodContext.LocStock(
                        Objects.requireNonNull(row.get(loc)),
                        Objects.requireNonNullElse(row.get(occupied), 0L),
                        Objects.requireNonNullElse(row.get(prodQty), 0L) > 0,
                        row.get(zon.bizDvsn) != null ? row.get(zon.bizDvsn).name() : null))
                .toList();
    }
}
