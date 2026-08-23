package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvResponse;
import com.project.wmsback.inventory.dto.InvSearchCond;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.mdm.prod.entity.TmpZon;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.mdm.prod.entity.QProd.prod;

@RequiredArgsConstructor
public class InvRepositoryImpl implements InvRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<InvResponse> search(InvSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(InvResponse.class,
                        inv.id,
                        prod.prodCd, prod.prodNm, prod.imgUrl, prod.tmpZon,
                        loc.locCd, loc.zon.zonCd, loc.locTyp,
                        lot.lotNo, lot.expiryDt,
                        inv.onHandQty, inv.alocQty, inv.hldQty,
                        InvQueryExpressions.avalQty()))
                .from(inv)
                .innerJoin(inv.prod, prod)
                .innerJoin(inv.loc, loc)
                .innerJoin(inv.lot, lot)
                .where(
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        locCdContains(cond.getLocCd()),
                        lotNoContains(cond.getLotNo()),
                        tmpZonEq(cond.getTmpZon()),
                        locTypEq(cond.getLocTyp()),
                        // 보유 0 행은 재고가 빠지는 시점에 삭제되지만, 과거 데이터의 잔여 0 행이 화면에 뜨지 않도록 방어적으로 항상 제외
                        inv.onHandQty.gt(0L)
                )
                // FEFO 관점에서 유통기한 임박 순이 유용하지만, 조회 화면은 상품→로케이션→유통기한 순이 읽기 편하다
                .orderBy(prod.prodCd.asc(), loc.locCd.asc(), lot.expiryDt.asc().nullsLast())
                .fetch();
    }

    @Override
    public List<Inv> searchStorageByScope(String zonCd, Long locId, Long prodId) {
        return queryFactory
                .selectFrom(inv)
                .innerJoin(inv.loc, loc).fetchJoin()
                .innerJoin(inv.prod, prod).fetchJoin()
                .innerJoin(inv.lot, lot).fetchJoin()
                .where(
                        // 조사 대상은 보관 재고뿐이다 (v1) — 스테이징 재고는 적치·출고확정이 소진 중인 물량이라
                        // 세는 시점 자체가 불안정하고, 보류·이동의 STORAGE 한정과도 결이 같다
                        loc.locTyp.eq(LocTyp.STORAGE),
                        zonCdEq(zonCd),
                        locIdEq(locId),
                        prodIdEq(prodId),
                        inv.onHandQty.gt(0L)
                )
                .orderBy(loc.locCd.asc(), prod.prodCd.asc(), lot.lotNo.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression zonCdEq(String zonCd) {
        return StringUtils.hasText(zonCd) ? loc.zon.zonCd.eq(zonCd) : null;
    }

    private BooleanExpression locIdEq(Long locId) {
        return locId != null ? loc.id.eq(locId) : null;
    }

    private BooleanExpression prodIdEq(Long prodId) {
        return prodId != null ? prod.id.eq(prodId) : null;
    }

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression locCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? loc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression lotNoContains(String lotNo) {
        return StringUtils.hasText(lotNo) ? lot.lotNo.containsIgnoreCase(lotNo) : null;
    }

    private BooleanExpression tmpZonEq(TmpZon tmpZon) {
        return tmpZon != null ? prod.tmpZon.eq(tmpZon) : null;
    }

    private BooleanExpression locTypEq(LocTyp locTyp) {
        return locTyp != null ? loc.locTyp.eq(locTyp) : null;
    }
}
