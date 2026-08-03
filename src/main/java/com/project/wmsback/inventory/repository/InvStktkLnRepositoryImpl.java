package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvStktkLnResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.entity.QInvStktkLn.invStktkLn;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.mdm.prod.entity.QProd.prod;

@RequiredArgsConstructor
public class InvStktkLnRepositoryImpl implements InvStktkLnRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<InvStktkLnResponse> searchByStktkId(Long stktkId) {
        return queryFactory
                .select(Projections.constructor(InvStktkLnResponse.class,
                        invStktkLn.id,
                        prod.prodCd, prod.prodNm, prod.tmpZon,
                        loc.locCd, loc.zonCd,
                        lot.lotNo, lot.expiryDt,
                        invStktkLn.sysQty,
                        // 현재 재고 — 라인이 가리키는 재고 행이 없을 수 있다(수동 추가 라인 · 전량 소진).
                        // 그 경우 null로 오고 DTO 생성자가 0으로 바꾼다.
                        inv.onHandQty, inv.alocQty, inv.hldQty,
                        invStktkLn.stktkQty, invStktkLn.cfmSysQty,
                        invStktkLn.rsnCd, invStktkLn.rsnDscr))
                .from(invStktkLn)
                .innerJoin(invStktkLn.prod, prod)
                .innerJoin(invStktkLn.loc, loc)
                .innerJoin(invStktkLn.lot, lot)
                // 재고 키(상품+Loc+Lot)로 붙는 연관관계 없는 조인 — inv는 라인의 자식이 아니라 같은 키의 스냅샷이다
                .leftJoin(inv).on(
                        inv.prod.id.eq(invStktkLn.prod.id),
                        inv.loc.id.eq(invStktkLn.loc.id),
                        inv.lot.id.eq(invStktkLn.lot.id))
                .where(invStktkLn.invStktk.id.eq(stktkId))
                // 실사 동선 그대로 — 로케이션 → 상품 → Lot 순
                .orderBy(loc.locCd.asc(), prod.prodCd.asc(), lot.lotNo.asc())
                .fetch();
    }
}
