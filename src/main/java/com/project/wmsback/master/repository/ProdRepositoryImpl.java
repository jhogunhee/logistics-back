package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.ProdSearchCond;
import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.master.entity.TempZone;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.project.wmsback.master.entity.QProd.prod;

@RequiredArgsConstructor
public class ProdRepositoryImpl implements ProdRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Prod> findByIdForUpdate(Long id) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(prod)
                        .where(prod.id.eq(id))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne());
    }

    @Override
    public List<Prod> search(ProdSearchCond cond) {
        return queryFactory
                .selectFrom(prod)
                .where(
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        tempZoneEq(cond.getTmpZon())
                )
                .orderBy(prod.id.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression tempZoneEq(TempZone tmpZon) {
        return tmpZon != null ? prod.tmpZon.eq(tmpZon) : null;
    }
}