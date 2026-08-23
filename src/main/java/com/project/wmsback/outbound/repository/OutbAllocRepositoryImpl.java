package com.project.wmsback.outbound.repository;

import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.outbound.dto.AllocLineResponse;
import com.project.wmsback.outbound.dto.AllocRowResponse;
import com.project.wmsback.outbound.dto.AllocTargetSearchCond;
import com.project.wmsback.outbound.dto.AllocWaveResponse;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.mdm.store.entity.QStore.store;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.repository.InvQueryExpressions.avalQty;
import static com.project.wmsback.inventory.repository.InvQueryExpressions.fefoOrder;
import static com.project.wmsback.outbound.entity.QOutbAlloc.outbAlloc;
import static com.project.wmsback.outbound.entity.QOutbLine.outbLine;
import static com.project.wmsback.outbound.entity.QOutbOrder.outbOrder;
import static com.project.wmsback.outbound.entity.QOutbWave.outbWave;

@RequiredArgsConstructor
public class OutbAllocRepositoryImpl implements OutbAllocRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 라인 하나의 기할당 합계. 상관 서브쿼리로 뽑는다 — {@code outb_alloc}을 조인하면
     * 라인이 할당 행 수만큼 불어나 {@code odr_qty} 합계가 함께 부풀기 때문이다(fan-out).
     */
    private static NumberExpression<Long> alocQtyOf(com.project.wmsback.outbound.entity.QOutbLine line) {
        return Expressions.numberTemplate(Long.class, "coalesce({0}, 0L)",
                JPAExpressions.select(outbAlloc.alocQty.sum())
                        .from(outbAlloc)
                        .where(outbAlloc.outbLine.eq(line)));
    }

    /**
     * 할당 대상 웨이브 목록.
     *
     * <p><b>쿼리를 둘로 나눈다.</b> 주문수량과 할당수량을 한 번에 뽑으려면 라인과 할당을 함께
     * 조인해야 하는데, 그러면 라인이 할당 행 수만큼 불어나 {@code odr_qty} 합계가 같이 부푼다
     * (fan-out). 할당 합계를 별도 쿼리로 뽑아 메모리에서 붙이면 그 문제가 사라지고, 집계 함수로
     * 감싼 상관 서브쿼리 같은 방언 의존 구문도 쓰지 않게 된다. 웨이브 건수가 작아 대가가 없다.
     *
     * <p><b>주문 쪽 검색조건은 전부 EXISTS다</b> — 상품·출고번호·점포뿐 아니라 출고예정일도 그렇다.
     * 조건을 바깥 WHERE에 붙이면 조건에 맞는 라인만 합계에 들어가 「이 웨이브의 주문수량」이 실제보다
     * 작게 나오는데, 할당 합계는 웨이브 전체를 세므로 <b>둘의 기준이 어긋나 잔량이 틀어진다</b>.
     * 실행 단위가 웨이브라 합계도 웨이브 전체여야 한다 — 조건은 「어느 웨이브를 보여줄지」만 정한다.
     */
    @Override
    public List<AllocWaveResponse> searchTargetWaves(AllocTargetSearchCond cond) {
        List<Tuple> waveRows = queryFactory
                .select(outbWave.id, outbWave.wavNo, outbWave.status, outbWave.wavStgyId, outbWave.createdAt,
                        outbOrder.expctDe.min(), outbOrder.id.countDistinct(), outbLine.odrQty.sum())
                .from(outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .join(outbOrder.wave, outbWave)
                .where(
                        // 웨이브 상태로 거르지 않는다 — ISSUED 웨이브도 잔량이 남으면 대상이다.
                        // 결품 종결이 잔량을 사후에 키우고, 지시취소 후 할당해제가 할당 0건 주문을
                        // 남기는데, 그 둘의 출구가 여기다. 늘어난 몫은 새 할당 행 + 새 지시 행으로 간다
                        // 출고확정된 주문은 집계에서도 뺀다 — findTargetLines가 거르는 것과 한 쌍이라,
                        // 안 빼면 잔량이 그 주문에만 남은 웨이브가 목록에 올라 실행하면 0건이 된다
                        outbOrder.status.ne(OutbStatus.SHIPPED),
                        waveNoContains(cond.getWavNo()),
                        matchingLineExists(cond)
                )
                .groupBy(outbWave.id, outbWave.wavNo, outbWave.status, outbWave.wavStgyId, outbWave.createdAt)
                .orderBy(outbWave.id.desc())
                .fetch();

        List<Long> wavIds = waveRows.stream().map(row -> row.get(outbWave.id)).toList();
        Map<Long, Long> alocByWave = sumAlocQtyByWaveIds(wavIds);

        List<AllocWaveResponse> result = new ArrayList<>(waveRows.size());
        for (Tuple row : waveRows) {
            Long wavId = row.get(outbWave.id);
            long odrQty = orZero(row.get(outbLine.odrQty.sum()));
            long alocQty = alocByWave.getOrDefault(wavId, 0L);
            // 잔량이 남은 웨이브만. 라인별 과할당이 막혀 있어(SUM(aloc) <= odr_qty)
            // 합계 잔량 > 0 과 「잔량 있는 라인의 존재」가 같은 뜻이 된다.
            if (odrQty <= alocQty) {
                continue;
            }
            result.add(AllocWaveResponse.of(wavId, row.get(outbWave.wavNo),
                    row.get(outbWave.status),
                    row.get(outbWave.wavStgyId), row.get(outbOrder.expctDe.min()),
                    row.get(outbWave.createdAt),
                    orZero(row.get(outbOrder.id.countDistinct())), odrQty, alocQty));
        }
        return result;
    }

    private Map<Long, Long> sumAlocQtyByWaveIds(List<Long> wavIds) {
        Map<Long, Long> map = new HashMap<>();
        if (wavIds.isEmpty()) {
            return map;
        }
        List<Tuple> rows = queryFactory
                .select(outbOrder.wave.id, outbAlloc.alocQty.sum())
                .from(outbAlloc)
                .join(outbAlloc.outbLine, outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .where(outbOrder.wave.id.in(wavIds))
                .groupBy(outbOrder.wave.id)
                .fetch();
        for (Tuple row : rows) {
            map.put(row.get(outbOrder.wave.id), orZero(row.get(outbAlloc.alocQty.sum())));
        }
        return map;
    }

    @Override
    public List<AllocLineResponse> lineRows(Long wavId) {
        List<Tuple> rows = queryFactory
                .select(outbLine.id, outbOrder.id, outbOrder.outbNo,
                        outbOrder.store.id, outbOrder.store.storeNm, outbOrder.expctDe,
                        outbLine.prod.id, outbLine.prod.prodCd, outbLine.prod.prodNm,
                        outbLine.odrQty, alocQtyOf(outbLine))
                .from(outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .where(outbOrder.wave.id.eq(wavId))
                .orderBy(outbOrder.outbNo.asc(), outbLine.id.asc())
                .fetch();

        List<AllocLineResponse> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            result.add(new AllocLineResponse(
                    row.get(outbLine.id), row.get(outbOrder.id), row.get(outbOrder.outbNo),
                    row.get(outbOrder.store.id), row.get(outbOrder.store.storeNm),
                    row.get(outbOrder.expctDe),
                    row.get(outbLine.prod.id), row.get(outbLine.prod.prodCd), row.get(outbLine.prod.prodNm),
                    orZero(row.get(outbLine.odrQty)), orZero(row.get(alocQtyOf(outbLine)))));
        }
        return result;
    }

    @Override
    public List<AllocRowResponse> allocRows(Long wavId) {
        List<Tuple> rows = queryFactory
                .select(outbAlloc.id, outbLine.id,
                        inv.id, inv.loc.id, inv.loc.locCd, inv.lot.id, inv.lot.lotNo, inv.lot.expiryDt,
                        outbAlloc.alocQty, outbAlloc.pikngQty, outbAlloc.alocStgyId)
                .from(outbAlloc)
                .join(outbAlloc.outbLine, outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .join(outbAlloc.inv, inv)
                .where(outbOrder.wave.id.eq(wavId))
                .orderBy(outbOrder.outbNo.asc(), outbLine.id.asc(), outbAlloc.id.asc())
                .fetch();

        List<AllocRowResponse> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            result.add(new AllocRowResponse(
                    row.get(outbAlloc.id), row.get(outbLine.id),
                    row.get(inv.id), row.get(inv.loc.id), row.get(inv.loc.locCd),
                    row.get(inv.lot.id), row.get(inv.lot.lotNo), row.get(inv.lot.expiryDt),
                    orZero(row.get(outbAlloc.alocQty)), orZero(row.get(outbAlloc.pikngQty)),
                    row.get(outbAlloc.alocStgyId)));
        }
        return result;
    }

    @Override
    public List<OutbLine> findTargetLines(List<Long> wavIds) {
        if (wavIds == null || wavIds.isEmpty()) {
            return List.of();
        }
        // 정렬이 곧 우선권이다 — 상품별로 모아 처리하되(prod_id), 그룹 안에서는 도착순(출고번호)이
        // 재고를 먼저 가져간다. 끝에 라인 id를 붙여 언제 돌려도 같은 결과가 나오게 한다.
        // 조인에 별칭을 주고 정렬·조건에서도 그 별칭을 쓴다 — 암시적 경로(outbLine.prod.id)를
        // 섞으면 같은 테이블에 조인이 하나 더 생긴다.
        return queryFactory
                .selectFrom(outbLine)
                .join(outbLine.outbOrder, outbOrder).fetchJoin()
                .join(outbLine.prod, prod).fetchJoin()
                .join(outbOrder.store, store).fetchJoin()
                .where(
                        outbOrder.wave.id.in(wavIds),
                        // 출고확정된 주문은 잔량이 남아 있어도 대상이 아니다 — 부족 출고로 닫힌
                        // 몫을 뒤늦게 채우면 확정을 무르는 셈이 된다 (recalcStatus의 SHIPPED 예외와 한 쌍)
                        outbOrder.status.ne(OutbStatus.SHIPPED),
                        outbLine.odrQty.gt(alocQtyOf(outbLine))
                )
                .orderBy(prod.id.asc(), outbOrder.expctDe.asc(),
                        outbOrder.outbNo.asc(), outbLine.id.asc())
                .fetch();
    }

    @Override
    public Map<Long, Long> sumAlocQtyByLineIds(List<Long> outbLineIds) {
        if (outbLineIds == null || outbLineIds.isEmpty()) {
            return Map.of();
        }
        List<Tuple> rows = queryFactory
                .select(outbAlloc.outbLine.id, outbAlloc.alocQty.sum())
                .from(outbAlloc)
                .where(outbAlloc.outbLine.id.in(outbLineIds))
                .groupBy(outbAlloc.outbLine.id)
                .fetch();

        Map<Long, Long> result = new HashMap<>();
        for (Tuple row : rows) {
            result.put(row.get(outbAlloc.outbLine.id), orZero(row.get(outbAlloc.alocQty.sum())));
        }
        return result;
    }

    @Override
    public List<Inv> findCandidates(Long prodId) {
        return queryFactory
                .selectFrom(inv)
                .join(inv.loc).fetchJoin()
                .join(inv.lot).fetchJoin()
                .where(candidatePredicates(prodId))
                .orderBy(fefoOrder(inv.lot, inv.loc))
                .fetch();
    }

    @Override
    public List<Long> findCandidateIds(Long prodId) {
        return queryFactory
                .select(inv.id)
                .from(inv)
                .join(inv.loc)
                .join(inv.lot)
                .where(candidatePredicates(prodId))
                .orderBy(fefoOrder(inv.lot, inv.loc))
                .fetch();
    }

    private BooleanExpression[] candidatePredicates(Long prodId) {
        return new BooleanExpression[]{
                inv.prod.id.eq(prodId),
                // 스테이징 재고는 후보가 아니다 — 피킹이 「보관 → SHIP-STAGE」라
                // 스테이징을 할당하면 피킹이 성립하지 않는다
                inv.loc.locTyp.eq(LocTyp.STORAGE),
                // 가용 = 보유 − 예약 − 보류. 보류분이 여기서 빠진다 (정의는 InvQueryExpressions 한 곳)
                avalQty().gt(0L)
        };
    }

    // ── 검색 조건 ────────────────────────────────────────────────────────────

    private BooleanExpression waveNoContains(String wavNo) {
        return StringUtils.hasText(wavNo) ? outbWave.wavNo.containsIgnoreCase(wavNo) : null;
    }

    /**
     * 주문 쪽 검색조건(상품·출고번호·점포·출고예정일) — <b>웨이브를 거른다.</b>
     * 조건에 맞는 라인이 하나라도 있으면 그 웨이브가 통째로 걸리고, 합계는 웨이브 전체로 낸다.
     */
    private BooleanExpression matchingLineExists(AllocTargetSearchCond cond) {
        boolean hasProd = StringUtils.hasText(cond.getProdCd());
        boolean hasOutbNo = StringUtils.hasText(cond.getOutbNo());
        boolean hasStore = cond.getStoreId() != null;
        boolean hasFrom = cond.getExpctDeFrom() != null;
        boolean hasTo = cond.getExpctDeTo() != null;
        if (!hasProd && !hasOutbNo && !hasStore && !hasFrom && !hasTo) {
            return null;
        }
        var line = new com.project.wmsback.outbound.entity.QOutbLine("matchLine");
        var order = new com.project.wmsback.outbound.entity.QOutbOrder("matchOrder");
        return JPAExpressions.selectOne()
                .from(line)
                .join(line.outbOrder, order)
                .where(
                        order.wave.id.eq(outbWave.id),
                        hasProd ? line.prod.prodCd.containsIgnoreCase(cond.getProdCd()) : null,
                        hasOutbNo ? order.outbNo.containsIgnoreCase(cond.getOutbNo()) : null,
                        hasStore ? order.store.id.eq(cond.getStoreId()) : null,
                        hasFrom ? order.expctDe.goe(cond.getExpctDeFrom()) : null,
                        hasTo ? order.expctDe.loe(cond.getExpctDeTo()) : null
                )
                .exists();
    }

    private static long orZero(Long value) {
        return value != null ? value : 0L;
    }
}
