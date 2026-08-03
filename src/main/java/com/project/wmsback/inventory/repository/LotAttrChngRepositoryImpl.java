package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.LotAttrChngResponse;
import com.project.wmsback.inventory.dto.LotAttrChngSearchCond;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.wmsback.inventory.entity.QLotAttrChng.lotAttrChng;

@RequiredArgsConstructor
public class LotAttrChngRepositoryImpl implements LotAttrChngRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<LotAttrChngResponse> search(LotAttrChngSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(LotAttrChngResponse.class,
                        lotAttrChng.id, lotAttrChng.lot.id,
                        prod.prodCd, prod.prodNm,
                        lotAttrChng.lotNo,
                        lotAttrChng.bfrMfgDt, lotAttrChng.aftMfgDt,
                        lotAttrChng.bfrExpiryDt, lotAttrChng.aftExpiryDt,
                        lotAttrChng.rsnCd, lotAttrChng.rsnDscr,
                        lotAttrChng.createdAt, lotAttrChng.createdBy))
                .from(lotAttrChng)
                .innerJoin(lotAttrChng.prod, prod)
                .where(
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        lotNoContains(cond.getLotNo()),
                        rsnCdEq(cond.getRsnCd()),
                        chngFromGoe(cond.getChngFrom()),
                        chngToLt(cond.getChngTo())
                )
                .orderBy(lotAttrChng.id.desc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    /** Lot 번호는 이력에 스냅샷으로 있으므로 lot 조인 없이 걸린다 (로그 자기완결의 이점) */
    private BooleanExpression lotNoContains(String lotNo) {
        return StringUtils.hasText(lotNo) ? lotAttrChng.lotNo.containsIgnoreCase(lotNo) : null;
    }

    private BooleanExpression rsnCdEq(String rsnCd) {
        return StringUtils.hasText(rsnCd) ? lotAttrChng.rsnCd.eq(rsnCd) : null;
    }

    private BooleanExpression chngFromGoe(LocalDate from) {
        return from != null ? lotAttrChng.createdAt.goe(from.atStartOfDay()) : null;
    }

    /** To는 그날을 포함한다 — 다음날 00:00 미만으로 비교해 시각 성분이 잘리지 않게 한다 */
    private BooleanExpression chngToLt(LocalDate to) {
        return to != null ? lotAttrChng.createdAt.lt(to.plusDays(1).atStartOfDay()) : null;
    }
}
