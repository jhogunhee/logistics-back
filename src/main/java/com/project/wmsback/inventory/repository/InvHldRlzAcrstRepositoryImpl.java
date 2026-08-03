package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvHldAcrstResponse;
import com.project.wmsback.inventory.dto.InvHldAcrstSearchCond;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.inventory.entity.QInvHldRlzAcrst.invHldRlzAcrst;
import static com.project.wmsback.master.entity.QLoc.loc;
import static com.project.wmsback.master.entity.QLot.lot;
import static com.project.wmsback.master.entity.QProd.prod;

@RequiredArgsConstructor
public class InvHldRlzAcrstRepositoryImpl implements InvHldRlzAcrstRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<InvHldAcrstResponse> search(InvHldAcrstSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(InvHldAcrstResponse.class,
                        invHldRlzAcrst.id, invHldRlzAcrst.hldNo,
                        prod.prodCd, prod.prodNm,
                        loc.locCd, lot.lotNo,
                        invHldRlzAcrst.rlzQty,
                        invHldRlzAcrst.rsnCd, invHldRlzAcrst.rsnDscr,
                        invHldRlzAcrst.createdAt))
                .from(invHldRlzAcrst)
                .innerJoin(invHldRlzAcrst.prod, prod)
                .innerJoin(invHldRlzAcrst.loc, loc)
                .innerJoin(invHldRlzAcrst.lot, lot)
                .where(
                        hldNoContains(cond.getHldNo()),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        locCdContains(cond.getLocCd()),
                        lotNoContains(cond.getLotNo()),
                        rsnCdEq(cond.getRsnCd())
                )
                .orderBy(invHldRlzAcrst.id.desc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression hldNoContains(String hldNo) {
        return StringUtils.hasText(hldNo) ? invHldRlzAcrst.hldNo.containsIgnoreCase(hldNo) : null;
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

    private BooleanExpression rsnCdEq(String rsnCd) {
        return StringUtils.hasText(rsnCd) ? invHldRlzAcrst.rsnCd.eq(rsnCd) : null;
    }
}
