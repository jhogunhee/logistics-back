package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvLotChngResponse;
import com.project.wmsback.inventory.dto.InvLotChngSearchCond;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.inventory.entity.QInvLotChng.invLotChng;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.mdm.prod.entity.QProd.prod;

@RequiredArgsConstructor
public class InvLotChngRepositoryImpl implements InvLotChngRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<InvLotChngResponse> search(InvLotChngSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(InvLotChngResponse.class,
                        invLotChng.id, invLotChng.lotChngNo,
                        prod.prodCd, prod.prodNm,
                        loc.locCd,
                        invLotChng.fromLotNo, invLotChng.fromMfgDt, invLotChng.fromExpiryDt,
                        invLotChng.toLotNo, invLotChng.toMfgDt, invLotChng.toExpiryDt,
                        invLotChng.chngQty, invLotChng.toLotNewYn,
                        invLotChng.rsnCd, invLotChng.rsnDscr,
                        invLotChng.createdAt))
                .from(invLotChng)
                .innerJoin(invLotChng.prod, prod)
                .innerJoin(invLotChng.loc, loc)
                .where(
                        lotChngNoContains(cond.getLotChngNo()),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        locCdContains(cond.getLocCd()),
                        lotNoContains(cond.getLotNo()),
                        rsnCdEq(cond.getRsnCd())
                )
                .orderBy(invLotChng.id.desc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression lotChngNoContains(String lotChngNo) {
        return StringUtils.hasText(lotChngNo) ? invLotChng.lotChngNo.containsIgnoreCase(lotChngNo) : null;
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

    /** 원/목적지 어느 쪽이든 매치 — 스냅샷 컬럼 검색이라 Lot 조인이 필요 없다 */
    private BooleanExpression lotNoContains(String lotNo) {
        return StringUtils.hasText(lotNo)
                ? invLotChng.fromLotNo.containsIgnoreCase(lotNo).or(invLotChng.toLotNo.containsIgnoreCase(lotNo))
                : null;
    }

    private BooleanExpression rsnCdEq(String rsnCd) {
        return StringUtils.hasText(rsnCd) ? invLotChng.rsnCd.eq(rsnCd) : null;
    }
}
