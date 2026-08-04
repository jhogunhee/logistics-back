package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;
import static com.project.mdm.vendor.entity.QVendor.vendor;

@RequiredArgsConstructor
public class IbOrderRepositoryImpl implements IbOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<IbOrder> search(IbOrderSearchCond cond) {
        // 라인(집계용)과 벤더(응답의 벤더명)를 fetch join으로 함께 로딩 (N+1 방지)
        return queryFactory
                .selectFrom(ibOrder).distinct()
                .leftJoin(ibOrder.lines).fetchJoin()
                .innerJoin(ibOrder.vendor, vendor).fetchJoin()
                .where(
                        ibNoContains(cond.getIbNo()),
                        statusEq(cond.getStatus()),
                        vndrNmContains(cond.getVndrNm()),
                        expctDeGoe(cond.getDateFrom()),
                        expctDeLoe(cond.getDateTo())
                )
                .orderBy(ibOrder.id.desc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression ibNoContains(String ibNo) {
        return StringUtils.hasText(ibNo) ? ibOrder.ibNo.containsIgnoreCase(ibNo) : null;
    }

    private BooleanExpression statusEq(IbStatus status) {
        return status != null ? ibOrder.status.eq(status) : null;
    }

    private BooleanExpression vndrNmContains(String vndrNm) {
        return StringUtils.hasText(vndrNm) ? vendor.vndrNm.containsIgnoreCase(vndrNm) : null;
    }

    private BooleanExpression expctDeGoe(LocalDate dateFrom) {
        return dateFrom != null ? ibOrder.expctDe.goe(dateFrom) : null;
    }

    private BooleanExpression expctDeLoe(LocalDate dateTo) {
        return dateTo != null ? ibOrder.expctDe.loe(dateTo) : null;
    }
}
