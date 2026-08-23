package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.dto.ShmtOrderResponse;
import com.project.wmsback.outbound.dto.ShmtSearchCond;
import com.project.wmsback.outbound.dto.ShmtWaveResponse;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.project.wmsback.outbound.entity.QOutbAlloc.outbAlloc;
import static com.project.wmsback.outbound.entity.QOutbLine.outbLine;
import static com.project.wmsback.outbound.entity.QOutbOrder.outbOrder;
import static com.project.wmsback.outbound.entity.QOutbWave.outbWave;

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
                        outbTypEq(cond.getOutbTyp()),
                        vhclFltnoEq(cond.getVhclFltno()),
                        waveIdEq(cond.getWavId()),
                        unassigned(cond.getUnassigned()),
                        expctDeGoe(cond.getExpctDeFrom()),
                        expctDeLoe(cond.getExpctDeTo())
                )
                .orderBy(outbOrder.id.desc())
                .fetch();
    }

    @Override
    public List<Long> searchIds(OutbOrderSearchCond cond) {
        return queryFactory
                .select(outbOrder.id)
                .from(outbOrder)
                .where(
                        outbNoContains(cond.getOutbNo()),
                        statusEq(cond.getStatus()),
                        storeIdEq(cond.getStoreId()),
                        outbTypEq(cond.getOutbTyp()),
                        vhclFltnoEq(cond.getVhclFltno()),
                        waveIdEq(cond.getWavId()),
                        unassigned(cond.getUnassigned()),
                        expctDeGoe(cond.getExpctDeFrom()),
                        expctDeLoe(cond.getExpctDeTo())
                )
                .orderBy(outbOrder.id.asc())
                .fetch();
    }

    // ── 출고확정 ─────────────────────────────────────────────────────────────

    @Override
    public List<ShmtWaveResponse> searchShmtWaves(ShmtSearchCond cond) {
        // 상태별 건수는 주문 단위 CASE 합이다 — 확정대상 = PICKED + CREATED(전량 미출고), 작업중 = 나머지
        NumberExpression<Long> ready = countIf(outbOrder.status.in(OutbStatus.PICKED, OutbStatus.CREATED));
        NumberExpression<Long> working = countIf(outbOrder.status.in(OutbStatus.ALLOCATED, OutbStatus.PICKING));
        NumberExpression<Long> shipped = countIf(outbOrder.status.eq(OutbStatus.SHIPPED));
        List<Tuple> rows = queryFactory
                .select(outbWave.id, outbWave.wavNo, outbOrder.expctDe.min(), outbWave.issuedDt,
                        outbOrder.id.count(), ready, working, shipped)
                .from(outbOrder)
                .join(outbOrder.wave, outbWave)
                .where(
                        // 종료(CLOSED)는 끝났고 편성중(PLANNED)은 아직 집품 전이다 — 확정할 것이 있는 곳은 ISSUED뿐
                        outbWave.status.eq(WaveStatus.ISSUED),
                        StringUtils.hasText(cond.getWavNo()) ? outbWave.wavNo.containsIgnoreCase(cond.getWavNo()) : null,
                        shmtOrderMatches(cond)
                )
                .groupBy(outbWave.id, outbWave.wavNo, outbWave.issuedDt)
                .orderBy(outbWave.id.desc())
                .fetch();

        List<ShmtWaveResponse> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            result.add(new ShmtWaveResponse(row.get(outbWave.id), row.get(outbWave.wavNo),
                    row.get(outbOrder.expctDe.min()), row.get(outbWave.issuedDt),
                    orZero(row.get(outbOrder.id.count())), orZero(row.get(ready)),
                    orZero(row.get(working)), orZero(row.get(shipped))));
        }
        return result;
    }

    @Override
    public List<ShmtOrderResponse> shmtOrders(Long wavId) {
        // 수량 셋은 전부 라인·할당 집계다 — 주문 헤더에 수량 컬럼이 없다(「상태와 수량의 분담」)
        JPQLQuery<Long> odrQty = JPAExpressions.select(outbLine.odrQty.sum().coalesce(0L))
                .from(outbLine).where(outbLine.outbOrder.eq(outbOrder));
        JPQLQuery<Long> alocQty = JPAExpressions.select(outbAlloc.alocQty.sum().coalesce(0L))
                .from(outbAlloc).join(outbAlloc.outbLine, outbLine).where(outbLine.outbOrder.eq(outbOrder));
        JPQLQuery<Long> pikngQty = JPAExpressions.select(outbAlloc.pikngQty.sum().coalesce(0L))
                .from(outbAlloc).join(outbAlloc.outbLine, outbLine).where(outbLine.outbOrder.eq(outbOrder));
        List<Tuple> rows = queryFactory
                .select(outbOrder.id, outbOrder.outbNo, outbOrder.store.storeNm, outbOrder.status,
                        odrQty, alocQty, pikngQty, outbOrder.shmtDt)
                .from(outbOrder)
                .where(outbOrder.wave.id.eq(wavId))
                .orderBy(outbOrder.outbNo.asc())
                .fetch();

        List<ShmtOrderResponse> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            result.add(ShmtOrderResponse.of(row.get(outbOrder.id), row.get(outbOrder.outbNo),
                    row.get(outbOrder.store.storeNm), row.get(outbOrder.status),
                    orZero(row.get(odrQty)), orZero(row.get(alocQty)), orZero(row.get(pikngQty)),
                    row.get(outbOrder.shmtDt)));
        }
        return result;
    }

    /** 주문 쪽 검색조건 — 할당·피킹지시 화면과 같은 EXISTS. 어느 웨이브를 보여줄지만 정하고 건수는 웨이브 전체다 */
    private BooleanExpression shmtOrderMatches(ShmtSearchCond cond) {
        boolean hasOutbNo = StringUtils.hasText(cond.getOutbNo());
        boolean hasStore = cond.getStoreId() != null;
        boolean hasFrom = cond.getExpctDeFrom() != null;
        boolean hasTo = cond.getExpctDeTo() != null;
        if (!hasOutbNo && !hasStore && !hasFrom && !hasTo) {
            return null;
        }
        var order = new com.project.wmsback.outbound.entity.QOutbOrder("matchOrder");
        return JPAExpressions.selectOne()
                .from(order)
                .where(
                        order.wave.id.eq(outbWave.id),
                        hasOutbNo ? order.outbNo.containsIgnoreCase(cond.getOutbNo()) : null,
                        hasStore ? order.store.id.eq(cond.getStoreId()) : null,
                        hasFrom ? order.expctDe.goe(cond.getExpctDeFrom()) : null,
                        hasTo ? order.expctDe.loe(cond.getExpctDeTo()) : null
                )
                .exists();
    }

    private static NumberExpression<Long> countIf(BooleanExpression when) {
        return new CaseBuilder().when(when).then(1L).otherwise(0L).sum();
    }

    private static long orZero(Long value) {
        return value != null ? value : 0L;
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

    private BooleanExpression waveIdEq(Long wavId) {
        return wavId != null ? outbOrder.wave.id.eq(wavId) : null;
    }

    private BooleanExpression outbTypEq(String outbTyp) {
        return StringUtils.hasText(outbTyp) ? outbOrder.outbTyp.eq(outbTyp) : null;
    }

    /** 배차 미정(NULL)은 어떤 편수로도 걸리지 않는다 — 편성 조건 판정(NE/NOT_IN 규약)과 같은 취급 */
    private BooleanExpression vhclFltnoEq(String vhclFltno) {
        return StringUtils.hasText(vhclFltno) ? outbOrder.vhclFltno.eq(vhclFltno) : null;
    }

    /** 웨이브 편성 화면의 후보 조회용 — 미편성(TRUE)/편성됨(FALSE) 필터 */
    private BooleanExpression unassigned(Boolean unassigned) {
        if (unassigned == null) {
            return null;
        }
        return unassigned ? outbOrder.wave.isNull() : outbOrder.wave.isNotNull();
    }

    /** 기간 조건은 출고예정일을 본다 — 주문일이 아니다. 웨이브는 「같은 날 나갈 주문」을 묶는 단위다 */
    private BooleanExpression expctDeGoe(LocalDate expctDeFrom) {
        return expctDeFrom != null ? outbOrder.expctDe.goe(expctDeFrom) : null;
    }

    private BooleanExpression expctDeLoe(LocalDate expctDeTo) {
        return expctDeTo != null ? outbOrder.expctDe.loe(expctDeTo) : null;
    }
}
