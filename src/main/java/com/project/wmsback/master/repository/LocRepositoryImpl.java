package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.LocSearchCond;
import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.LocType;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.master.entity.QLoc.loc;

@RequiredArgsConstructor
public class LocRepositoryImpl implements LocRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Loc> search(LocSearchCond cond) {
        return queryFactory
                .selectFrom(loc)
                .where(
                        locCdContains(cond.getLocCd()),
                        zoneCdEq(cond.getZoneCd()),
                        locTypeEq(cond.getLocType())
                )
                .orderBy(loc.id.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression locCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? loc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression zoneCdEq(String zoneCd) {
        return StringUtils.hasText(zoneCd) ? loc.zoneCd.eq(zoneCd) : null;
    }

    private BooleanExpression locTypeEq(LocType locType) {
        return locType != null ? loc.locType.eq(locType) : null;
    }
}
