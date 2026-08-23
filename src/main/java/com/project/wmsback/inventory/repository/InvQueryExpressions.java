package com.project.wmsback.inventory.repository;

import com.project.wmsback.warehouse.entity.QLoc;
import com.project.wmsback.warehouse.entity.QLot;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberExpression;

import static com.project.wmsback.inventory.entity.QInv.inv;

/**
 * 재고 조회가 공유하는 QueryDSL 식의 단일 정의 — 가용수량과 FEFO 정렬.
 * 할당·정기보충·Lot변경·재고조회가 각자 같은 식을 베껴 쓰면 한 곳만 고쳐도 나머지가 조용히 어긋난다
 * (「할당이 다음에 집을 Lot」과 「보충이 먼저 옮기는 Lot」이 갈라진 채 주석만 같다고 주장하게 된다).
 */
public final class InvQueryExpressions {

    private InvQueryExpressions() {
    }

    /** 가용 = 보유 − 예약 − 보류 ({@code Inv#avalQty}의 쿼리판). 보류분이 여기서 빠진다 */
    public static NumberExpression<Long> avalQty() {
        return inv.onHandQty.subtract(inv.alocQty).subtract(inv.hldQty);
    }

    /**
     * FEFO — 유통기한 ASC(NULL 맨 뒤) → 피킹순위 → 로케이션코드 → inv id(결정성).
     * Lot·로케이션 경로를 호출자가 넘긴다 — 명시 join 별칭을 쓰는 쿼리와 {@code inv.lot}/{@code inv.loc}
     * 암시 경로를 쓰는 쿼리가 섞여 있어, 여기서 경로를 고정하면 한쪽에 join이 하나 더 생긴다
     */
    public static OrderSpecifier<?>[] fefoOrder(QLot lot, QLoc loc) {
        return new OrderSpecifier<?>[]{
                lot.expiryDt.asc().nullsLast(),
                loc.pikngPrty.asc(),
                loc.locCd.asc(),
                inv.id.asc()
        };
    }
}
