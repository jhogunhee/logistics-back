package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.wmsback.outbound.entity.QOutbOrder.outbOrder;

@RequiredArgsConstructor
public class OutbOrderRepositoryImpl implements OutbOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OutbOrder> search(OutbOrderSearchCond cond) {
        // 응답의 점포명/웨이브번호/라인 집계에 필요한 연관을 함께 로딩 (N+1 방지).
        // 단일값 fetch join(store, wave)은 여러 개 가능하고, 컬렉션(lines)은 하나뿐이라 MultipleBag 문제 없음.
        return queryFactory
                .selectFrom(outbOrder).distinct()
                .join(outbOrder.store).fetchJoin()
                .leftJoin(outbOrder.wave).fetchJoin()
                .leftJoin(outbOrder.lines).fetchJoin()
                .where(
                        outbNoContains(cond.getOutbNo()),
                        statusEq(cond.getStatus()),
                        storeIdEq(cond.getStoreId()),
                        waveIdEq(cond.getWaveId()),
                        unassigned(cond.getUnassigned()),
                        orderDtGoe(cond.getDateFrom()),
                        orderDtLoe(cond.getDateTo())
                )
                .orderBy(outbOrder.id.desc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression outbNoContains(String outbNo) {
        return StringUtils.hasText(outbNo) ? outbOrder.outbNo.containsIgnoreCase(outbNo) : null;
    }

    private BooleanExpression statusEq(OutbStatus status) {
        return status != null ? outbOrder.status.eq(status) : null;
    }

    private BooleanExpression storeIdEq(Long storeId) {
        return storeId != null ? outbOrder.store.id.eq(storeId) : null;
    }

    private BooleanExpression waveIdEq(Long waveId) {
        return waveId != null ? outbOrder.wave.id.eq(waveId) : null;
    }

    /** 웨이브 편성 화면의 후보 조회용 — 미편성(TRUE)/편성됨(FALSE) 필터 */
    private BooleanExpression unassigned(Boolean unassigned) {
        if (unassigned == null) {
            return null;
        }
        return unassigned ? outbOrder.wave.isNull() : outbOrder.wave.isNotNull();
    }

    private BooleanExpression orderDtGoe(LocalDate dateFrom) {
        return dateFrom != null ? outbOrder.orderDt.goe(dateFrom) : null;
    }

    private BooleanExpression orderDtLoe(LocalDate dateTo) {
        return dateTo != null ? outbOrder.orderDt.loe(dateTo) : null;
    }
}
