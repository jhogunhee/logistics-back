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

@RequiredArgsConstructor
public class IbOrderRepositoryImpl implements IbOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<IbOrder> search(IbOrderSearchCond cond) {
        // 목록 응답의 라인 집계(lineCount 등)에 필요한 라인을 fetch join으로 함께 로딩 (N+1 방지)
        return queryFactory
                .selectFrom(ibOrder).distinct()
                .leftJoin(ibOrder.lines).fetchJoin()
                .where(
                        ibNoContains(cond.getIbNo()),
                        statusEq(cond.getStatus()),
                        expctDtGoe(cond.getDateFrom()),
                        expctDtLoe(cond.getDateTo())
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

    private BooleanExpression expctDtGoe(LocalDate dateFrom) {
        return dateFrom != null ? ibOrder.expctDt.goe(dateFrom) : null;
    }

    private BooleanExpression expctDtLoe(LocalDate dateTo) {
        return dateTo != null ? ibOrder.expctDt.loe(dateTo) : null;
    }
}
