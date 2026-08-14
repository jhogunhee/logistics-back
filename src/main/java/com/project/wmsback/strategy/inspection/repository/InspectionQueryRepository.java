package com.project.wmsback.strategy.inspection.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.warehouse.entity.QLot.lot;

/** 검수 규칙이 쓰는 재고·로트 조회 포트. 규칙은 InspectionContext를 통해 이걸 받는다 */
@Repository
@RequiredArgsConstructor
public class InspectionQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 해당 상품의 "보유 재고(on_hand>0)가 있는 로트" 중 최신 제조일자.
     * 역순제한(LOT_DATE_REVERSE)의 기준값 — excludeReceiptDt가 있으면 그 입고일자 로트는
     * 계산에서 제외한다 ("같은 날 들어온 로트는 기준으로 삼지 않는다"를 파라미터로 받는 것).
     * <p>
     * 입고일자가 NULL인 로트는 <b>남긴다</b>. 제외 대상은 「그 날 들어온 것」이고 입고일자를
     * 모르는 로트는 그 날 들어왔다고 볼 근거가 없다 — {@code ne}만 쓰면 NULL 비교가 unknown이라
     * 조용히 빠져서, 당일제외 옵션 하나로 기준 로트 집합이 달라진다.
     */
    public LocalDate latestMfgDtWithStock(Long prodId, LocalDate excludeReceiptDt) {
        return queryFactory
                .select(lot.mfgDt.max())
                .from(inv)
                .join(inv.lot, lot)
                .where(
                        inv.prod.id.eq(prodId),
                        inv.onHandQty.gt(0),
                        lot.mfgDt.isNotNull(),
                        excludeReceiptDt != null
                                ? lot.receiptDt.isNull().or(lot.receiptDt.ne(excludeReceiptDt))
                                : null
                )
                .fetchOne();
    }
}
