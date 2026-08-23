package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.dto.AsnRef;
import com.project.omsback.inbound.dto.OmsIbOrderSearchCond;
import com.project.omsback.inbound.entity.OmsIbOrder;
import com.project.omsback.inbound.entity.OmsIbStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static com.project.omsback.inbound.entity.QOmsIbLine.omsIbLine;
import static com.project.omsback.inbound.entity.QOmsIbOrder.omsIbOrder;
import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;
import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.mdm.vendor.entity.QVendor.vendor;

@RequiredArgsConstructor
public class OmsIbOrderRepositoryImpl implements OmsIbOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OmsIbOrder> search(OmsIbOrderSearchCond cond) {
        // 라인(집계용)과 라인의 상품(단위·환산 합계용), 벤더(응답의 코드/명)를 fetch join으로 함께 로딩 (N+1 방지).
        // 환산이 마저 읽는 prod.uoms까지 여기서 당기면 컬렉션 fetch join이 둘이 돼 못 쓴다(MultipleBagFetch)
        // — 그쪽은 hibernate.default_batch_fetch_size가 IN 쿼리로 묶는다.
        return queryFactory
                .selectFrom(omsIbOrder).distinct()
                .leftJoin(omsIbOrder.lines, omsIbLine).fetchJoin()
                .leftJoin(omsIbLine.prod, prod).fetchJoin()
                .innerJoin(omsIbOrder.vendor, vendor).fetchJoin()
                .where(
                        omsIbNoContains(cond.getOmsIbNo()),
                        vndrNmContains(cond.getVndrNm()),
                        statusIn(cond.getStatus()),
                        expctDeGoe(cond.getDateFrom()),
                        expctDeLoe(cond.getDateTo())
                )
                // 입고예정일 → 주문번호 오름차순: 창고가 받을 순서대로 읽힌다.
                // 주문번호가 예정일 기준 채번(PO-YYYYMMDD-NNN)이라 같은 날짜 안에서는 채번 순이 된다.
                .orderBy(omsIbOrder.expctDe.asc(), omsIbOrder.omsIbNo.asc())
                .fetch();
    }

    @Override
    public List<AsnRef> findAsnRefs(Collection<Long> omsIbOrderIds) {
        if (omsIbOrderIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
                .select(Projections.constructor(AsnRef.class,
                        ibOrder.omsIbOrderId, ibOrder.id, ibOrder.ibNo, ibOrder.status))
                .from(ibOrder)
                .where(ibOrder.omsIbOrderId.in(omsIbOrderIds))
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression omsIbNoContains(String omsIbNo) {
        return StringUtils.hasText(omsIbNo) ? omsIbOrder.omsIbNo.containsIgnoreCase(omsIbNo) : null;
    }

    /** 벤더는 이제 마스터라 검색도 조인 대상 컬럼(vendor.vndrNm)을 본다 */
    private BooleanExpression vndrNmContains(String vndrNm) {
        return StringUtils.hasText(vndrNm) ? omsIbOrder.vendor.vndrNm.containsIgnoreCase(vndrNm) : null;
    }

    private BooleanExpression statusIn(List<OmsIbStatus> status) {
        return status != null && !status.isEmpty() ? omsIbOrder.status.in(status) : null;
    }

    private BooleanExpression expctDeGoe(LocalDate dateFrom) {
        return dateFrom != null ? omsIbOrder.expctDe.goe(dateFrom) : null;
    }

    private BooleanExpression expctDeLoe(LocalDate dateTo) {
        return dateTo != null ? omsIbOrder.expctDe.loe(dateTo) : null;
    }
}
