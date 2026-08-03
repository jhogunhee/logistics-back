package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.dto.LocSearchCond;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.warehouse.entity.QLoc.loc;

@RequiredArgsConstructor
public class LocRepositoryImpl implements LocRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Loc> search(LocSearchCond cond) {
        return queryFactory
                .selectFrom(loc)
                .where(
                        locCdContains(cond.getLocCd()),
                        zonCdEq(cond.getZonCd()),
                        locTypEq(cond.getLocTyp())
                )
                .orderBy(loc.zonCd.asc(), loc.locCd.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression locCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? loc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression zonCdEq(String zonCd) {
        return StringUtils.hasText(zonCd) ? loc.zonCd.eq(zonCd) : null;
    }

    private BooleanExpression locTypEq(LocTyp locTyp) {
        return locTyp != null ? loc.locTyp.eq(locTyp) : null;
    }
}
