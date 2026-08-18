package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.dto.PutawayCandidateResponse;
import com.project.wmsback.inbound.dto.PutawaySearchCond;
import com.project.wmsback.inbound.entity.IbLine;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.wmsback.inbound.entity.QIbLine.ibLine;
import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.mdm.vendor.entity.QVendor.vendor;

@RequiredArgsConstructor
public class IbLineRepositoryImpl implements IbLineRepositoryCustom {

    /** 검수 합격분이 머무는 입고 스테이징. ReceivingService/PutawayService의 상수와 동일 */
    private static final String STAGING_LOC_CD = "RCV-STAGE";

    private final JPAQueryFactory queryFactory;

    @Override
    public List<IbLine> findAllByOrderIdWithProd(Long ibOrderId) {
        // 응답이 낱개수량(Prod.eaQtyOf → prod.uoms)을 쓰지만 포장 컬렉션은 fetch join하지 않는다 —
        // 컬렉션을 붙이면 라인 하나가 포장 수만큼 행으로 불어난다. 지연로딩은
        // default_batch_fetch_size(100)가 IN 한 방으로 묶으므로 쿼리만 하나 더 든다.
        return queryFactory
                .selectFrom(ibLine)
                .innerJoin(ibLine.prod, prod).fetchJoin()
                .where(ibLine.ibOrder.id.eq(ibOrderId))
                .orderBy(ibLine.id.asc())
                .fetch();
    }

    @Override
    public List<PutawayCandidateResponse> findAllPendingPutawayBatches(PutawaySearchCond cond) {
        // inv_hist는 (ib_line_id, lot_id)로 RECEIVE(+)/ADJUST(-)/MOVE 출고분(-)이 섞여 쌓이므로,
        // 스테이징 로케이션 한정으로 합산하면 그 배치의 미적치 잔량이 그대로 나온다.
        return queryFactory
                .select(Projections.constructor(PutawayCandidateResponse.class,
                        ibLine.id, ibOrder.id, ibOrder.ibNo, vendor.vndrNm,
                        prod.prodCd, prod.prodNm, prod.tmpZon,
                        lot.id, lot.lotNo, lot.receiptDt, lot.expiryDt,
                        invHist.qty.sum()))
                .from(invHist)
                .innerJoin(ibLine).on(invHist.ibLineId.eq(ibLine.id))
                .innerJoin(ibLine.ibOrder, ibOrder)
                .innerJoin(ibOrder.vendor, vendor)
                .innerJoin(ibLine.prod, prod)
                .innerJoin(invHist.lot, lot)
                .where(
                        invHist.loc.locCd.eq(STAGING_LOC_CD),
                        invHist.ibLineId.isNotNull(),
                        ibNoContains(cond.getIbNo()),
                        vndrNmContains(cond.getVndrNm()),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        receiptDtGoe(cond.getDateFrom()),
                        receiptDtLoe(cond.getDateTo())
                )
                .groupBy(ibLine.id, ibOrder.id, ibOrder.ibNo, vendor.vndrNm,
                        prod.prodCd, prod.prodNm, prod.tmpZon,
                        lot.id, lot.lotNo, lot.receiptDt, lot.expiryDt)
                .having(invHist.qty.sum().gt(0L))
                .orderBy(lot.expiryDt.asc().nullsLast(), ibLine.id.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression ibNoContains(String ibNo) {
        return StringUtils.hasText(ibNo) ? ibOrder.ibNo.containsIgnoreCase(ibNo) : null;
    }

    private BooleanExpression vndrNmContains(String vndrNm) {
        return StringUtils.hasText(vndrNm) ? vendor.vndrNm.containsIgnoreCase(vndrNm) : null;
    }

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression receiptDtGoe(LocalDate dateFrom) {
        return dateFrom != null ? lot.receiptDt.goe(dateFrom) : null;
    }

    private BooleanExpression receiptDtLoe(LocalDate dateTo) {
        return dateTo != null ? lot.receiptDt.loe(dateTo) : null;
    }
}
