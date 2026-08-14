package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inventory.entity.TxTyp;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
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
                        vndrNmContains(cond.getVndrNm()),
                        expctDeGoe(cond.getDateFrom()),
                        expctDeLoe(cond.getDateTo())
                )
                .orderBy(ibOrder.id.desc())
                .fetch();
    }

    @Override
    public Map<Long, LocalDateTime> lastReceiveDtByLine(Collection<Long> ibLineIds) {
        if (ibLineIds.isEmpty()) {
            return new HashMap<>();
        }
        // inv_hist.ib_line_id는 FK도 연관관계도 아닌 스칼라라 엔티티 조인이 안 된다 —
        // id로 걸러 그 컬럼으로 직접 group by 한다 (PutawayTaskQueryRepository.stagingQtyOfBatch와 같은 방식)
        DateTimePath<LocalDateTime> createdAt = invHist.createdAt;
        List<Tuple> rows = queryFactory
                .select(invHist.ibLineId, createdAt.max())
                .from(invHist)
                .where(
                        invHist.ibLineId.in(ibLineIds),
                        invHist.txTyp.eq(TxTyp.RECEIVE)
                )
                .groupBy(invHist.ibLineId)
                .fetch();

        Map<Long, LocalDateTime> byLine = new HashMap<>();
        for (Tuple row : rows) {
            byLine.put(row.get(invHist.ibLineId), row.get(createdAt.max()));
        }
        return byLine;
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression ibNoContains(String ibNo) {
        return StringUtils.hasText(ibNo) ? ibOrder.ibNo.containsIgnoreCase(ibNo) : null;
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
