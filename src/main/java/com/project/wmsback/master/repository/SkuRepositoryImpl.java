package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.SkuSearchCond;
import com.project.wmsback.master.entity.Sku;
import com.project.wmsback.master.entity.TempZone;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.project.wmsback.master.entity.QSku.sku;

@RequiredArgsConstructor
public class SkuRepositoryImpl implements SkuRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Sku> findByIdForUpdate(Long id) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(sku)
                        .where(sku.id.eq(id))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne());
    }

    @Override
    public List<Sku> search(SkuSearchCond cond) {
        return queryFactory
                .selectFrom(sku)
                .where(
                        skuCdContains(cond.getSkuCd()),
                        skuNmContains(cond.getSkuNm()),
                        tempZoneEq(cond.getTempZone())
                )
                .orderBy(sku.id.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression skuCdContains(String skuCd) {
        return StringUtils.hasText(skuCd) ? sku.skuCd.containsIgnoreCase(skuCd) : null;
    }

    private BooleanExpression skuNmContains(String skuNm) {
        return StringUtils.hasText(skuNm) ? sku.skuNm.containsIgnoreCase(skuNm) : null;
    }

    private BooleanExpression tempZoneEq(TempZone tempZone) {
        return tempZone != null ? sku.tempZone.eq(tempZone) : null;
    }
}