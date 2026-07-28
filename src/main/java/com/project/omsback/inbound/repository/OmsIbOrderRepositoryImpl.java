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

import static com.project.omsback.inbound.entity.QOmsIbOrder.omsIbOrder;
import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;
import static com.project.wmsback.master.entity.QVendor.vendor;

@RequiredArgsConstructor
public class OmsIbOrderRepositoryImpl implements OmsIbOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OmsIbOrder> search(OmsIbOrderSearchCond cond) {
        // 라인(집계용)과 벤더(응답의 코드/명)를 fetch join으로 함께 로딩 (N+1 방지)
        return queryFactory
                .selectFrom(omsIbOrder).distinct()
                .leftJoin(omsIbOrder.lines).fetchJoin()
                .innerJoin(omsIbOrder.vendor, vendor).fetchJoin()
                .where(
                        omsIbNoContains(cond.getOmsIbNo()),
                        vndrNmContains(cond.getVndrNm()),
                        statusEq(cond.getStatus()),
                        expctDtGoe(cond.getDateFrom()),
                        expctDtLoe(cond.getDateTo())
                )
                .orderBy(omsIbOrder.id.desc())
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

    private BooleanExpression statusEq(OmsIbStatus status) {
        return status != null ? omsIbOrder.status.eq(status) : null;
    }

    private BooleanExpression expctDtGoe(LocalDate dateFrom) {
        return dateFrom != null ? omsIbOrder.expctDt.goe(dateFrom) : null;
    }

    private BooleanExpression expctDtLoe(LocalDate dateTo) {
        return dateTo != null ? omsIbOrder.expctDt.loe(dateTo) : null;
    }
}
