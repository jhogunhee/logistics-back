package com.project.omsback.outbound.repository;

import com.project.omsback.outbound.dto.OmsOutbOrderSearchCond;
import com.project.omsback.outbound.dto.OutbOrderRef;
import com.project.omsback.outbound.entity.OmsOutbOrder;
import com.project.omsback.outbound.entity.OmsOutbStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.mdm.store.entity.QStore.store;
import static com.project.omsback.outbound.entity.QOmsOutbLine.omsOutbLine;
import static com.project.omsback.outbound.entity.QOmsOutbOrder.omsOutbOrder;
import static com.project.wmsback.outbound.entity.QOutbOrder.outbOrder;
import static com.project.wmsback.outbound.entity.QOutbWave.outbWave;

@RequiredArgsConstructor
public class OmsOutbOrderRepositoryImpl implements OmsOutbOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OmsOutbOrder> search(OmsOutbOrderSearchCond cond) {
        // 라인(집계용)과 라인의 상품(응답의 코드/명), 점포(응답의 코드/명)를 fetch join으로 함께 로딩 (N+1 방지).
        // 컬렉션 fetch join은 행을 곱하므로 distinct로 헤더 중복을 걷어낸다.
        return queryFactory
                .selectFrom(omsOutbOrder).distinct()
                .leftJoin(omsOutbOrder.lines, omsOutbLine).fetchJoin()
                .leftJoin(omsOutbLine.prod, prod).fetchJoin()
                .innerJoin(omsOutbOrder.store, store).fetchJoin()
                .where(
                        omsOutbNoContains(cond.getOmsOutbNo()),
                        storeNmContains(cond.getStoreNm()),
                        statusEq(cond.getStatus()),
                        outbTypEq(cond.getOutbTyp()),
                        vhclFltnoEq(cond.getVhclFltno()),
                        expctDeGoe(cond.getDateFrom()),
                        expctDeLoe(cond.getDateTo())
                )
                // 출고예정일 → 주문번호 오름차순: 창고가 내보낼 순서대로 읽힌다.
                // 주문번호가 예정일 기준 채번(SO-YYYYMMDD-NNN)이라 같은 날짜 안에서는 채번 순이 된다.
                .orderBy(omsOutbOrder.expctDe.asc(), omsOutbOrder.omsOutbNo.asc())
                .fetch();
    }

    @Override
    public List<OutbOrderRef> findOutbOrderRefs(Collection<Long> omsOutbOrderIds) {
        if (omsOutbOrderIds.isEmpty()) {
            return List.of();
        }
        return queryFactory
                .select(Projections.constructor(OutbOrderRef.class,
                        outbOrder.omsOutbOrderId, outbOrder.id, outbOrder.outbNo,
                        outbOrder.status, outbWave.wavNo))
                .from(outbOrder)
                // 웨이브는 명시적 별칭으로 건다 — 프로젝션에서 outbOrder.wave.wavNo를 쓰면
                // 암시적 조인이 하나 더 생겨 미편성 주문이 결과에서 빠진다
                .leftJoin(outbOrder.wave, outbWave)
                .where(outbOrder.omsOutbOrderId.in(omsOutbOrderIds))
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression omsOutbNoContains(String omsOutbNo) {
        return StringUtils.hasText(omsOutbNo) ? omsOutbOrder.omsOutbNo.containsIgnoreCase(omsOutbNo) : null;
    }

    /** 납품처는 마스터라 검색도 조인 대상 컬럼(store.storeNm)을 본다 */
    private BooleanExpression storeNmContains(String storeNm) {
        return StringUtils.hasText(storeNm) ? omsOutbOrder.store.storeNm.containsIgnoreCase(storeNm) : null;
    }

    private BooleanExpression statusEq(OmsOutbStatus status) {
        return status != null ? omsOutbOrder.status.eq(status) : null;
    }

    private BooleanExpression outbTypEq(String outbTyp) {
        return StringUtils.hasText(outbTyp) ? omsOutbOrder.outbTyp.eq(outbTyp) : null;
    }

    /** 배차 미정(NULL)은 어떤 편수로도 걸리지 않는다 — 웨이브 편성 조건 판정과 같은 취급 */
    private BooleanExpression vhclFltnoEq(String vhclFltno) {
        return StringUtils.hasText(vhclFltno) ? omsOutbOrder.vhclFltno.eq(vhclFltno) : null;
    }

    private BooleanExpression expctDeGoe(LocalDate dateFrom) {
        return dateFrom != null ? omsOutbOrder.expctDe.goe(dateFrom) : null;
    }

    private BooleanExpression expctDeLoe(LocalDate dateTo) {
        return dateTo != null ? omsOutbOrder.expctDe.loe(dateTo) : null;
    }
}
