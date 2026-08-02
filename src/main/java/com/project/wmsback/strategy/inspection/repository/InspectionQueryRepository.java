package com.project.wmsback.strategy.inspection.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.master.entity.QLot.lot;

/** 검수 규칙이 쓰는 재고·로트 조회 포트. 규칙은 InspectionContext를 통해 이걸 받는다 */
@Repository
@RequiredArgsConstructor
public class InspectionQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 해당 상품의 "보유 재고(on_hand>0)가 있는 로트" 중 최신 제조일자.
     * 역순제한(LOT_DATE_REVERSE)의 기준값 — excludeReceiptDt가 있으면 그 입고일자 로트는
     * 계산에서 제외한다 (레거시의 "당일 입고분 제외" 동작을 파라미터로 승격한 것).
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
                        excludeReceiptDt != null ? lot.receiptDt.ne(excludeReceiptDt) : null
                )
                .fetchOne();
    }
}
