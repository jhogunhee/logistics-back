package com.project.wmsback.inventory.repository;

import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.project.wmsback.inventory.dto.SpmtTargetSearchCond;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.repository.InvQueryExpressions.avalQty;
import static com.project.wmsback.inventory.repository.InvQueryExpressions.fefoOrder;
import static com.project.wmsback.warehouse.entity.QFxngLoc.fxngLoc;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.wmsback.warehouse.entity.QZon.zon;

/**
 * 보충(SPMT) 산정의 재고 현황 조회 — JPAQueryFactory만 드는 읽기 전용 조회 포트
 * (PutawayQueryRepository와 같은 형태, 3파일 삼각형 아님).
 */
@Repository
@RequiredArgsConstructor
public class SpmtQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 대상 후보 1건 — 고정로케이션과 그 자리의 지정 상품 현재고 합 (min 미달 판정은 서비스가 유입을 얹어 한다).
     * locMaxQty·locOnHandQty는 물리 적재가능 계산용(전 상품 기준, max_qty 없으면 null) — 추천이 발행 창구의
     * 용량 검증에 걸리지 않게 배정을 자르는 데 쓴다
     */
    public record TargetRow(Long fxngLocId, Long locId, String locCd, String zonCd,
                            Long prodId, String prodCd, String prodNm, TmpZon tmpZon,
                            long minQty, long maxQty, long onHandQty,
                            Long locMaxQty, long locOnHandQty) {
    }

    /** 원천 후보 1건 (FEFO 순 정렬 완료). avalQty = 보유 − 예약 − 보류 */
    public record SourceRow(Long prodId, Long invId, String fromLocCd, String lotNo,
                            LocalDate expiryDt, long avalQty) {
    }

    /**
     * 고정로케이션 전체의 지정 상품 현재고 집계. 현재고는 지정 상품만 합산한다 —
     * 전용 자리의 타상품 재고는 오염이지 보충 판정의 재고가 아니다 (uq_fxng_loc라 join 곱셈 오염 없음).
     */
    public List<TargetRow> targets(SpmtTargetSearchCond cond) {
        // inv는 로케이션 기준으로 붙이고 지정 상품 현재고는 CASE로 가른다 — 전상품 점유(물리 용량)와 한 번에 읽으려고
        // (PutawayQueryRepository.storageStocks와 같은 꼴)
        NumberExpression<Long> locOnHand = inv.onHandQty.sum().coalesce(0L);
        NumberExpression<Long> prodOnHand = new CaseBuilder()
                .when(inv.prod.id.eq(prod.id)).then(inv.onHandQty)
                .otherwise(0L)
                .sum().coalesce(0L);

        List<Tuple> rows = queryFactory
                .select(fxngLoc.id, loc.id, loc.locCd, zon.zonCd,
                        prod.id, prod.prodCd, prod.prodNm, prod.tmpZon,
                        fxngLoc.minQty, fxngLoc.maxQty, prodOnHand, loc.maxQty, locOnHand)
                .from(fxngLoc)
                .join(fxngLoc.loc, loc)
                .leftJoin(loc.zon, zon)
                .join(fxngLoc.prod, prod)
                .leftJoin(inv).on(inv.loc.eq(loc))
                .where(zonCdEq(cond), tmpZonEq(cond), prodCdContains(cond), prodNmContains(cond), locCdContains(cond))
                .groupBy(fxngLoc.id, loc.id, loc.locCd, zon.zonCd,
                        prod.id, prod.prodCd, prod.prodNm, prod.tmpZon,
                        fxngLoc.minQty, fxngLoc.maxQty, loc.maxQty)
                .orderBy(loc.locCd.asc())
                .fetch();

        return rows.stream()
                .map(row -> new TargetRow(
                        row.get(fxngLoc.id), row.get(loc.id), row.get(loc.locCd), row.get(zon.zonCd),
                        row.get(prod.id), row.get(prod.prodCd), row.get(prod.prodNm), row.get(prod.tmpZon),
                        Objects.requireNonNullElse(row.get(fxngLoc.minQty), 0L),
                        Objects.requireNonNullElse(row.get(fxngLoc.maxQty), 0L),
                        Objects.requireNonNullElse(row.get(prodOnHand), 0L),
                        row.get(loc.maxQty),
                        Objects.requireNonNullElse(row.get(locOnHand), 0L)))
                .toList();
    }

    /**
     * 보충 원천 후보 — 대상 상품들의 보관 가용재고를 FEFO 순으로.
     * 고정로케이션에 등재된 자리는 상품 불문 전부 제외한다 — 다른 피킹 자리를 헐어 채우지 않는다.
     * FEFO 정렬(유통기한 ASC NULLS LAST → 피킹순위 → 로케이션코드 → id)은 할당과 같은 정의다.
     */
    public List<SourceRow> sources(Collection<Long> prodIds) {
        if (prodIds.isEmpty()) {
            return List.of();
        }
        NumberExpression<Long> aval = avalQty();

        List<Tuple> rows = queryFactory
                .select(inv.prod.id, inv.id, loc.locCd, lot.lotNo, lot.expiryDt, aval)
                .from(inv)
                .join(inv.loc, loc)
                .join(inv.lot, lot)
                .where(
                        inv.prod.id.in(prodIds),
                        loc.locTyp.eq(LocTyp.STORAGE),
                        aval.gt(0L),
                        loc.id.notIn(JPAExpressions.select(fxngLoc.loc.id).from(fxngLoc))
                )
                .orderBy(fefoOrder(lot, loc))
                .fetch();

        return rows.stream()
                .map(row -> new SourceRow(
                        row.get(inv.prod.id), row.get(inv.id), row.get(loc.locCd),
                        row.get(lot.lotNo), row.get(lot.expiryDt),
                        Objects.requireNonNullElse(row.get(aval), 0L)))
                .toList();
    }

    /** 특정 로케이션의 지정 상품 현재고 합 — 발행 시점 부족량 재검증용 (targets의 onHand와 같은 정의) */
    public long prodOnHandQty(Long prodId, Long locId) {
        Long sum = queryFactory
                .select(inv.onHandQty.sum())
                .from(inv)
                .where(inv.prod.id.eq(prodId), inv.loc.id.eq(locId))
                .fetchOne();
        return sum != null ? sum : 0L;
    }

    // ── 검색 조건 ────────────────────────────────────────────────────────────

    private BooleanExpression zonCdEq(SpmtTargetSearchCond cond) {
        return StringUtils.hasText(cond.getZonCd()) ? zon.zonCd.eq(cond.getZonCd()) : null;
    }

    private BooleanExpression tmpZonEq(SpmtTargetSearchCond cond) {
        return cond.getTmpZon() != null ? prod.tmpZon.eq(cond.getTmpZon()) : null;
    }

    private BooleanExpression prodCdContains(SpmtTargetSearchCond cond) {
        return StringUtils.hasText(cond.getProdCd()) ? prod.prodCd.containsIgnoreCase(cond.getProdCd()) : null;
    }

    private BooleanExpression prodNmContains(SpmtTargetSearchCond cond) {
        return StringUtils.hasText(cond.getProdNm()) ? prod.prodNm.containsIgnoreCase(cond.getProdNm()) : null;
    }

    private BooleanExpression locCdContains(SpmtTargetSearchCond cond) {
        return StringUtils.hasText(cond.getLocCd()) ? loc.locCd.containsIgnoreCase(cond.getLocCd()) : null;
    }
}
