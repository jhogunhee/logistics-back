package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvAdjResponse;
import com.project.wmsback.inventory.dto.InvAdjSearchCond;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.wmsback.inventory.entity.QInvAdj.invAdj;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;

@RequiredArgsConstructor
public class InvAdjRepositoryImpl implements InvAdjRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<InvAdjResponse> search(InvAdjSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(InvAdjResponse.class,
                        invAdj.id, invAdj.adjNo,
                        prod.prodCd, prod.prodNm,
                        loc.locCd, lot.lotNo,
                        invAdj.adjBfrQty, invAdj.adjQty, invAdj.hldNo,
                        invAdj.rsnCd, invAdj.rsnDscr,
                        invAdj.createdAt))
                .from(invAdj)
                .innerJoin(invAdj.prod, prod)
                .innerJoin(invAdj.loc, loc)
                .innerJoin(invAdj.lot, lot)
                .where(
                        adjNoContains(cond.getAdjNo()),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        locCdContains(cond.getLocCd()),
                        lotNoContains(cond.getLotNo()),
                        rsnCdEq(cond.getRsnCd()),
                        hldOnlyEq(cond.getHldOnly()),
                        createdAtGoe(cond.getDateFrom()),
                        createdAtLt(cond.getDateTo())
                )
                .orderBy(invAdj.id.desc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression adjNoContains(String adjNo) {
        return StringUtils.hasText(adjNo) ? invAdj.adjNo.containsIgnoreCase(adjNo) : null;
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
        return StringUtils.hasText(rsnCd) ? invAdj.rsnCd.eq(rsnCd) : null;
    }

    /** 라인 종류 필터 — 구분 컬럼이 없으므로 hld_no의 존재 여부가 판정이다 (ix_inv_adj_hld) */
    private BooleanExpression hldOnlyEq(Boolean hldOnly) {
        if (hldOnly == null) {
            return null;
        }
        return hldOnly ? invAdj.hldNo.isNotNull() : invAdj.hldNo.isNull();
    }

    private BooleanExpression createdAtGoe(LocalDate dateFrom) {
        return dateFrom != null ? invAdj.createdAt.goe(dateFrom.atStartOfDay()) : null;
    }

    private BooleanExpression createdAtLt(LocalDate dateTo) {
        return dateTo != null ? invAdj.createdAt.lt(dateTo.plusDays(1).atStartOfDay()) : null;
    }
}
