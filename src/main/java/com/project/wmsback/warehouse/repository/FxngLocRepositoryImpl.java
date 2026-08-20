package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.dto.FxngLocSearchCond;
import com.project.wmsback.warehouse.entity.FxngLoc;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.wmsback.warehouse.entity.QFxngLoc.fxngLoc;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QZon.zon;

@RequiredArgsConstructor
public class FxngLocRepositoryImpl implements FxngLocRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<FxngLoc> search(FxngLocSearchCond cond) {
        // 응답이 상품 코드·명과 존 코드를 싣는다 — 행마다 지연 로딩하지 않게 함께 가져온다
        return queryFactory
                .selectFrom(fxngLoc)
                .join(fxngLoc.prod, prod).fetchJoin()
                .join(fxngLoc.loc, loc).fetchJoin()
                .join(loc.zon, zon).fetchJoin()
                .where(
                        prodCdContains(cond.getProdCd()),
                        locCdContains(cond.getLocCd()),
                        zonCdEq(cond.getZonCd())
                )
                .orderBy(loc.locCd.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression locCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? loc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression zonCdEq(String zonCd) {
        return StringUtils.hasText(zonCd) ? zon.zonCd.eq(zonCd) : null;
    }
}
