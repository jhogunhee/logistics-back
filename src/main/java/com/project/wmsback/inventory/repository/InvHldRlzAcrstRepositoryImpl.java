package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvHldAcrstResponse;
import com.project.wmsback.inventory.dto.InvHldAcrstSearchCond;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.wmsback.inventory.entity.QInvHldRlzAcrst.invHldRlzAcrst;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.mdm.prod.entity.QProd.prod;

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
                        rsnCdEq(cond.getRsnCd()),
                        createdAtGoe(cond.getDateFrom()),
                        createdAtLt(cond.getDateTo())
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

    private BooleanExpression createdAtGoe(LocalDate dateFrom) {
        return dateFrom != null ? invHldRlzAcrst.createdAt.goe(dateFrom.atStartOfDay()) : null;
    }

    private BooleanExpression createdAtLt(LocalDate dateTo) {
        return dateTo != null ? invHldRlzAcrst.createdAt.lt(dateTo.plusDays(1).atStartOfDay()) : null;
    }
}
