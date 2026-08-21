package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.PickingSearchCond;
import com.project.wmsback.outbound.dto.PickingWaveResponse;
import com.project.wmsback.outbound.dto.PikngRowResponse;
import com.project.wmsback.outbound.dto.PikngTaskSearchCond;
import com.project.wmsback.outbound.dto.PikngWaveDetailResponse;
import com.project.wmsback.outbound.dto.PikngWaveResponse;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.outbound.entity.QOutbAlloc.outbAlloc;
import static com.project.wmsback.outbound.entity.QOutbLine.outbLine;
import static com.project.wmsback.outbound.entity.QOutbOrder.outbOrder;
import static com.project.wmsback.outbound.entity.QOutbWave.outbWave;
import static com.project.wmsback.outbound.entity.QPikngTask.pikngTask;

@RequiredArgsConstructor
public class PikngTaskRepositoryImpl implements PikngTaskRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 피킹지시 화면의 웨이브 목록.
     *
     * <p><b>쿼리를 둘로 나눈다</b> — 주문수량과 할당·피킹 수량을 한 번에 조인하면 라인이 할당 행
     * 수만큼 불어나 합계가 부푼다(fan-out). 할당 쪽 합계를 별도 쿼리로 뽑아 메모리에서 붙인다
     * ({@code OutbAllocRepositoryImpl.searchTargetWaves}와 같은 구조·같은 이유).
     */
    @Override
    public List<PikngWaveResponse> searchTaskWaves(PikngTaskSearchCond cond) {
        List<Tuple> waveRows = queryFactory
                .select(outbWave.id, outbWave.wavNo, outbWave.status, outbWave.issuedDt,
                        outbOrder.expctDe.min(), outbOrder.id.countDistinct(), outbLine.odrQty.sum())
                .from(outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .join(outbOrder.wave, outbWave)
                .where(
                        outbWave.status.in(WaveStatus.PLANNED, WaveStatus.ISSUED),
                        statusEq(cond.getStatus()),
                        waveNoContains(cond.getWavNo()),
                        matchingLineExists(cond)
                )
                .groupBy(outbWave.id, outbWave.wavNo, outbWave.status, outbWave.issuedDt)
                .orderBy(outbWave.id.desc())
                .fetch();

        List<Long> wavIds = waveRows.stream().map(row -> row.get(outbWave.id)).toList();
        Map<Long, long[]> allocSums = sumAllocByWaveIds(wavIds);
        Map<Long, Long> pendingAllocs = countPendingAllocsByWaveIds(wavIds);

        List<PikngWaveResponse> result = new ArrayList<>(waveRows.size());
        for (Tuple row : waveRows) {
            Long wavId = row.get(outbWave.id);
            WaveStatus status = row.get(outbWave.status);
            long[] sums = allocSums.getOrDefault(wavId, new long[]{0L, 0L});
            // 할당이 0건인 PLANNED 웨이브는 발행 대상이 아니다 — 할당 화면의 일이라 여기서 뺀다.
            // ISSUED는 할당이 반드시 있지만(발행 가드) 조건을 상태로 걸어 의도를 남긴다.
            if (status == WaveStatus.PLANNED && sums[0] == 0L) {
                continue;
            }
            result.add(PikngWaveResponse.of(wavId, row.get(outbWave.wavNo), status,
                    row.get(outbOrder.expctDe.min()), row.get(outbWave.issuedDt),
                    orZero(row.get(outbOrder.id.countDistinct())), orZero(row.get(outbLine.odrQty.sum())),
                    sums[0], sums[1], pendingAllocs.getOrDefault(wavId, 0L)));
        }
        return result;
    }

    /**
     * 웨이브별 <b>아직 지시가 나가지 않은 할당</b> 건수. 발행 전이면 전 할당이 여기 세어지고,
     * 발행 후에 0이 아니면 「할당은 됐는데 지시가 안 나간 것」이 남아 있다는 뜻이다 —
     * 화면이 그것을 무조건 강조한다(추가 발행 대상).
     */
    private Map<Long, Long> countPendingAllocsByWaveIds(List<Long> wavIds) {
        if (wavIds.isEmpty()) {
            return Map.of();
        }
        List<Tuple> rows = queryFactory
                .select(outbOrder.wave.id, outbAlloc.count())
                .from(outbAlloc)
                .join(outbAlloc.outbLine, outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .where(outbOrder.wave.id.in(wavIds), liveTaskAbsent())
                .groupBy(outbOrder.wave.id)
                .fetch();

        Map<Long, Long> map = new HashMap<>();
        for (Tuple row : rows) {
            map.put(row.get(outbOrder.wave.id), orZero(row.get(outbAlloc.count())));
        }
        return map;
    }

    /**
     * 이 할당에 살아 있는 지시가 없다 — 「미발행 할당」의 정의이자 추가 발행의 대상 조건.
     * 취소된 지시는 세지 않는다(부분 유니크 {@code uq_pikng_task_alloc}와 같은 기준).
     */
    private static BooleanExpression liveTaskAbsent() {
        return JPAExpressions.selectOne()
                .from(pikngTask)
                .where(pikngTask.outbAlloc.eq(outbAlloc),
                        pikngTask.status.ne(PikngTaskStatus.CANCELLED))
                .exists().not();
    }

    /** 웨이브별 [할당수량 합, 피킹수량 합] */
    private Map<Long, long[]> sumAllocByWaveIds(List<Long> wavIds) {
        Map<Long, long[]> map = new HashMap<>();
        if (wavIds.isEmpty()) {
            return map;
        }
        List<Tuple> rows = queryFactory
                .select(outbOrder.wave.id, outbAlloc.alocQty.sum(), outbAlloc.pikngQty.sum())
                .from(outbAlloc)
                .join(outbAlloc.outbLine, outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .where(outbOrder.wave.id.in(wavIds))
                .groupBy(outbOrder.wave.id)
                .fetch();
        for (Tuple row : rows) {
            map.put(row.get(outbOrder.wave.id),
                    new long[]{orZero(row.get(outbAlloc.alocQty.sum())), orZero(row.get(outbAlloc.pikngQty.sum()))});
        }
        return map;
    }

    @Override
    public List<PikngRowResponse> allocRowsForIssue(Long wavId) {
        List<Tuple> rows = queryFactory
                .select(outbAlloc.id, outbOrder.outbNo, outbOrder.store.storeNm,
                        outbLine.prod.prodCd, outbLine.prod.prodNm,
                        inv.loc.locCd, inv.lot.lotNo, inv.lot.expiryDt,
                        outbAlloc.alocQty, outbAlloc.pikngQty)
                .from(outbAlloc)
                .join(outbAlloc.outbLine, outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .join(outbAlloc.inv, inv)
                // 살아 있는 지시가 붙은 할당은 뺀다 — 이 목록의 뜻이 「아직 안 나간 것」이다.
                // 발행 전에는 전 할당이 여기 오고(발행 미리보기), 발행 후에는 추가 발행 대상이 온다
                .where(outbOrder.wave.id.eq(wavId), liveTaskAbsent())
                // 발행 시 부여될 순서 그대로 정렬한다 — 이 목록이 곧 발행 미리보기다 (PikngTaskService.issue와 한 쌍)
                .orderBy(inv.loc.pikngPrty.asc(), inv.loc.locCd.asc(), outbAlloc.id.asc())
                .fetch();

        List<PikngRowResponse> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            result.add(PikngRowResponse.of(null, null, row.get(outbAlloc.id),
                    row.get(outbOrder.outbNo), row.get(outbOrder.store.storeNm),
                    row.get(outbLine.prod.prodCd), row.get(outbLine.prod.prodNm),
                    row.get(inv.loc.locCd), row.get(inv.lot.lotNo), row.get(inv.lot.expiryDt),
                    orZero(row.get(outbAlloc.alocQty)), orZero(row.get(outbAlloc.pikngQty)), null, null, null));
        }
        return result;
    }

    @Override
    public List<PikngRowResponse> taskRows(Long wavId) {
        List<Tuple> rows = queryFactory
                .select(pikngTask.id, pikngTask.srtSeq, pikngTask.outbAlloc.id,
                        outbOrder.outbNo, outbOrder.store.storeNm,
                        pikngTask.prod.prodCd, pikngTask.prod.prodNm,
                        pikngTask.fromLoc.locCd, pikngTask.lot.lotNo, pikngTask.lot.expiryDt,
                        pikngTask.drctQty, pikngTask.cmplQty, pikngTask.status, pikngTask.shotgeQty, pikngTask.shotgeRsnCd)
                .from(pikngTask)
                .join(pikngTask.outbAlloc, outbAlloc)
                .join(outbAlloc.outbLine, outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .where(pikngTask.wave.id.eq(wavId), pikngTask.status.ne(PikngTaskStatus.CANCELLED))
                .orderBy(pikngTask.srtSeq.asc())
                .fetch();

        List<PikngRowResponse> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            result.add(PikngRowResponse.of(row.get(pikngTask.id), row.get(pikngTask.srtSeq),
                    row.get(pikngTask.outbAlloc.id),
                    row.get(outbOrder.outbNo), row.get(outbOrder.store.storeNm),
                    row.get(pikngTask.prod.prodCd), row.get(pikngTask.prod.prodNm),
                    row.get(pikngTask.fromLoc.locCd), row.get(pikngTask.lot.lotNo), row.get(pikngTask.lot.expiryDt),
                    orZero(row.get(pikngTask.drctQty)), orZero(row.get(pikngTask.cmplQty)),
                    row.get(pikngTask.status), row.get(pikngTask.shotgeQty), row.get(pikngTask.shotgeRsnCd)));
        }
        return result;
    }

    @Override
    public List<PikngWaveDetailResponse.NoAllocOrder> noAllocOrders(Long wavId) {
        List<Tuple> rows = queryFactory
                .select(outbOrder.outbNo, outbOrder.store.storeNm)
                .from(outbOrder)
                .where(
                        outbOrder.wave.id.eq(wavId),
                        // 전량 미출고로 확정된 주문은 할당 0건인 채 SHIPPED다 — 발행을 막는 주문이 아니다
                        outbOrder.status.ne(OutbStatus.SHIPPED),
                        JPAExpressions.selectOne()
                                .from(outbAlloc)
                                .join(outbAlloc.outbLine, outbLine)
                                .where(outbLine.outbOrder.eq(outbOrder))
                                .exists().not()
                )
                .orderBy(outbOrder.outbNo.asc())
                .fetch();

        List<PikngWaveDetailResponse.NoAllocOrder> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            result.add(new PikngWaveDetailResponse.NoAllocOrder(
                    row.get(outbOrder.outbNo), row.get(outbOrder.store.storeNm)));
        }
        return result;
    }

    /**
     * 피킹 화면의 웨이브 목록. 지시(1행)와 주문의 조인은 1:1 경로(task → alloc → line → order)라
     * fan-out이 없어 쿼리를 나누지 않는다.
     *
     * <p>출고예정일은 바깥 WHERE에 직접 건다 — 편성 가드가 웨이브의 출고예정일을 하나로 강제하므로
     * 라인 일부만 걸러 합계가 틀어지는 일이 없다. 상품 조건만 EXISTS다(직접 걸면 합계가 좁혀진다).
     */
    @Override
    public List<PickingWaveResponse> searchPickingWaves(PickingSearchCond cond) {
        NumberExpression<Long> openTaskCount = openTaskCount();
        List<Tuple> rows = queryFactory
                .select(outbWave.id, outbWave.wavNo, outbWave.issuedDt,
                        outbOrder.expctDe.min(), pikngTask.drctQty.sum(), pikngTask.cmplQty.sum(),
                        openTaskCount)
                .from(pikngTask)
                .join(pikngTask.wave, outbWave)
                .join(pikngTask.outbAlloc, outbAlloc)
                .join(outbAlloc.outbLine, outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .where(
                        outbWave.status.eq(WaveStatus.ISSUED),
                        pikngTask.status.ne(PikngTaskStatus.CANCELLED),
                        waveNoContains(cond.getWavNo()),
                        cond.getExpctDeFrom() != null ? outbOrder.expctDe.goe(cond.getExpctDeFrom()) : null,
                        cond.getExpctDeTo() != null ? outbOrder.expctDe.loe(cond.getExpctDeTo()) : null,
                        matchingTaskProdExists(cond.getProdCd())
                )
                .groupBy(outbWave.id, outbWave.wavNo, outbWave.issuedDt)
                .orderBy(outbWave.id.desc())
                .fetch();

        List<PickingWaveResponse> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            result.add(PickingWaveResponse.of(row.get(outbWave.id), row.get(outbWave.wavNo),
                    row.get(outbOrder.expctDe.min()), row.get(outbWave.issuedDt),
                    orZero(row.get(pikngTask.drctQty.sum())), orZero(row.get(pikngTask.cmplQty.sum())),
                    orZero(row.get(openTaskCount))));
        }
        return result;
    }

    /**
     * 아직 닫히지 않은 지시 건수 — 집을 것이 남았거나 결품 종결이 필요한 지시다.
     * 결품 종결을 강제하는 마감·배치가 없어, 이 값이 「잊힌 잔량」의 유일한 신호다.
     */
    private static NumberExpression<Long> openTaskCount() {
        return new CaseBuilder()
                .when(pikngTask.status.eq(PikngTaskStatus.DIRECTED)
                        .and(pikngTask.cmplQty.lt(pikngTask.drctQty)))
                .then(1L).otherwise(0L)
                .sum();
    }

    // ── 검색 조건 ────────────────────────────────────────────────────────────

    private BooleanExpression statusEq(String status) {
        return StringUtils.hasText(status) ? outbWave.status.eq(WaveStatus.valueOf(status)) : null;
    }

    private BooleanExpression waveNoContains(String wavNo) {
        return StringUtils.hasText(wavNo) ? outbWave.wavNo.containsIgnoreCase(wavNo) : null;
    }

    /**
     * 주문 쪽 검색조건(상품·출고번호·점포·출고예정일) — <b>웨이브를 거른다.</b>
     * 조건에 맞는 라인이 하나라도 있으면 그 웨이브가 통째로 걸리고, 합계는 웨이브 전체로 낸다
     * ({@code OutbAllocRepositoryImpl.matchingLineExists}와 같은 규칙).
     */
    private BooleanExpression matchingLineExists(PikngTaskSearchCond cond) {
        boolean hasProd = StringUtils.hasText(cond.getProdCd());
        boolean hasOutbNo = StringUtils.hasText(cond.getOutbNo());
        boolean hasStore = StringUtils.hasText(cond.getStoreCd());
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
                        hasStore ? order.store.storeCd.containsIgnoreCase(cond.getStoreCd()) : null,
                        hasFrom ? order.expctDe.goe(cond.getExpctDeFrom()) : null,
                        hasTo ? order.expctDe.loe(cond.getExpctDeTo()) : null
                )
                .exists();
    }

    /** 상품 조건 — 그 상품의 지시가 있는 웨이브를 통째로 고른다 (합계는 웨이브 전체) */
    private BooleanExpression matchingTaskProdExists(String prodCd) {
        if (!StringUtils.hasText(prodCd)) {
            return null;
        }
        var task = new com.project.wmsback.outbound.entity.QPikngTask("matchTask");
        return JPAExpressions.selectOne()
                .from(task)
                .where(
                        task.wave.eq(outbWave),
                        task.status.ne(PikngTaskStatus.CANCELLED),
                        task.prod.prodCd.containsIgnoreCase(prodCd)
                )
                .exists();
    }

    private static long orZero(Long value) {
        return value != null ? value : 0L;
    }
}
