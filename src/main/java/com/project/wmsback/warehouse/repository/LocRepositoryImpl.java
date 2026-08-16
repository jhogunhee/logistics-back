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
import static com.project.wmsback.warehouse.entity.QZon.zon;

@RequiredArgsConstructor
public class LocRepositoryImpl implements LocRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Loc> search(LocSearchCond cond) {
        // 응답이 존 코드를 싣는다 — 로케이션마다 존을 지연 로딩하지 않게 함께 가져온다
        return queryFactory
                .selectFrom(loc)
                .join(loc.zon, zon).fetchJoin()
                .where(
                        locCdContains(cond.getLocCd()),
                        zonCdEq(cond.getZonCd()),
                        locTypEq(cond.getLocTyp())
                )
                .orderBy(zon.zonCd.asc(), loc.locCd.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression locCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? loc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression zonCdEq(String zonCd) {
        return StringUtils.hasText(zonCd) ? zon.zonCd.eq(zonCd) : null;
    }

    private BooleanExpression locTypEq(LocTyp locTyp) {
        return locTyp != null ? loc.locTyp.eq(locTyp) : null;
    }
}
