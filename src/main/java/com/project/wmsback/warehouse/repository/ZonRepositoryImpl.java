package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.dto.ZonSearchCond;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.entity.Zon;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.warehouse.entity.QZon.zon;

@RequiredArgsConstructor
public class ZonRepositoryImpl implements ZonRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Zon> search(ZonSearchCond cond) {
        return queryFactory
                .selectFrom(zon)
                .where(
                        zonCdContains(cond.getZonCd()),
                        tmpZonEq(cond.getTmpZon()),
                        bizDvsnEq(cond.getBizDvsn())
                )
                .orderBy(zon.zonCd.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression zonCdContains(String zonCd) {
        return StringUtils.hasText(zonCd) ? zon.zonCd.containsIgnoreCase(zonCd) : null;
    }

    private BooleanExpression tmpZonEq(TmpZon tmpZon) {
        return tmpZon != null ? zon.tmpZon.eq(tmpZon) : null;
    }

    private BooleanExpression bizDvsnEq(BizDvsn bizDvsn) {
        return bizDvsn != null ? zon.bizDvsn.eq(bizDvsn) : null;
    }
}
