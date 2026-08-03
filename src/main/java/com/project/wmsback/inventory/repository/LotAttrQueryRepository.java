package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.LotAttrTargetResponse;
import com.project.wmsback.inventory.dto.LotAttrTargetSearchCond;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.warehouse.entity.QLot.lot;

/**
 * 정정 대상 Lot 조회 (재고 속성변경 화면 상단).
 *
 * Spring Data 인터페이스 없이 JPAQueryFactory만 드는 읽기 전용 조회 포트다
 * (InspectionQueryRepository·PutawayQueryRepository와 같은 형태) — 조회 축이 lot이고
 * 집계가 inv라 어느 한쪽의 리포지토리 삼각형에 넣기 어렵다.
 *
 * 불변 전제가 여기서 강제된다: **유통기한 관리 상품(shelfLifeDays IS NOT NULL)의 Lot만** 내려간다.
 * 미관리 상품의 Lot은 제조일자·유통기한이 항상 NULL인 것이 그 상품의 정의이므로 정정 대상이 아니다
 * (docs/sg/wms-st-화면프로세스정의서.md 3-3). 서비스도 저장 시 같은 조건을 재검증한다.
 */
@Repository
@RequiredArgsConstructor
public class LotAttrQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * 조건에 맞는 Lot과 그 Lot의 영향 범위(재고 행 수 · 보유 합계)를 함께 돌려준다.
     * 재고가 하나도 없는 Lot도 기본적으로 포함된다 — 소진된 Lot의 유통기한 오기도 이력상 정정 대상이다
     * (걸러내려면 onlyInStock).
     */
    public List<LotAttrTargetResponse> searchTargets(LotAttrTargetSearchCond cond) {
        NumberExpression<Long> invRowCnt = inv.id.countDistinct();
        NumberExpression<Long> onHandQty = inv.onHandQty.sum().coalesce(0L);

        return queryFactory
                .select(Projections.constructor(LotAttrTargetResponse.class,
                        lot.id, prod.id, prod.prodCd, prod.prodNm, lot.lotNo,
                        lot.receiptDt, lot.mfgDt, lot.expiryDt, prod.shelfLifeDays,
                        invRowCnt, onHandQty))
                .from(lot)
                .innerJoin(lot.prod, prod)
                .leftJoin(inv).on(inv.lot.eq(lot))
                .where(
                        prod.shelfLifeDays.isNotNull(),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        lotNoContains(cond.getLotNo()),
                        expiryGoe(cond.getExpiryFrom()),
                        expiryLoe(cond.getExpiryTo())
                )
                .groupBy(lot.id, prod.id, prod.prodCd, prod.prodNm, lot.lotNo,
                        lot.receiptDt, lot.mfgDt, lot.expiryDt, prod.shelfLifeDays)
                .having(Boolean.TRUE.equals(cond.getOnlyInStock()) ? onHandQty.gt(0L) : null)
                .orderBy(lot.expiryDt.asc(), lot.lotNo.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression lotNoContains(String lotNo) {
        return StringUtils.hasText(lotNo) ? lot.lotNo.containsIgnoreCase(lotNo) : null;
    }

    private BooleanExpression expiryGoe(LocalDate from) {
        return from != null ? lot.expiryDt.goe(from) : null;
    }

    private BooleanExpression expiryLoe(LocalDate to) {
        return to != null ? lot.expiryDt.loe(to) : null;
    }
}
