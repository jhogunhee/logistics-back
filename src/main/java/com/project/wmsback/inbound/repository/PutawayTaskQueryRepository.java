package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.dto.PutawayTaskResponse;
import com.project.wmsback.inbound.dto.PutawayTaskSearchCond;
import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.project.wmsback.inbound.entity.QIbLine.ibLine;
import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;
import static com.project.wmsback.inbound.entity.QPutawayTask.putawayTask;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.mdm.store.entity.QStore.store;
import static com.project.mdm.vendor.entity.QVendor.vendor;

/**
 * 적치지시 조회 — 목록과 잔량 집계.
 * <p>
 * Spring Data 인터페이스 없이 {@code JPAQueryFactory}만 드는 읽기 전용 조회 포트다
 * (strategy의 PutawayQueryRepository와 같은 형태). 저장은 {@link PutawayTaskRepository}가 맡는다.
 */
@Repository
@RequiredArgsConstructor
public class PutawayTaskQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** 지시 목록. 미완료(DIRECTED)를 위로 올리고 그 안에서 유통기한 순 — 실행 화면이 이 순서를 작업 순서로 쓴다 */
    public List<PutawayTaskResponse> search(PutawayTaskSearchCond cond) {
        NumberExpression<Integer> openFirst = new CaseBuilder()
                .when(putawayTask.status.eq(PutawayTaskStatus.DIRECTED)).then(0)
                .otherwise(1);

        return queryFactory
                .select(Projections.constructor(PutawayTaskResponse.class,
                        putawayTask.id, ibLine.id, ibOrder.id, ibOrder.ibNo,
                        vendor.vndrNm, store.storeNm,
                        prod.prodCd, prod.prodNm, prod.tmpZon,
                        lot.id, lot.lotNo, lot.receiptDt, lot.expiryDt,
                        loc.id, loc.locCd,
                        putawayTask.drctQty, putawayTask.cmplQty,
                        putawayTask.drctQty.subtract(putawayTask.cmplQty),
                        putawayTask.status, putawayTask.createdAt, putawayTask.cmplDt))
                .from(putawayTask)
                .innerJoin(putawayTask.ibLine, ibLine)
                .innerJoin(ibLine.ibOrder, ibOrder)
                // 상대처는 둘 중 하나만 있다(벤더 또는 점포) — FK가 없어 leftJoin (IbLineRepositoryImpl과 같다)
                .leftJoin(ibOrder.vendor, vendor)
                .leftJoin(ibOrder.store, store)
                .innerJoin(ibLine.prod, prod)
                .innerJoin(putawayTask.lot, lot)
                .innerJoin(putawayTask.toLoc, loc)
                .where(
                        ibNoContains(cond.getIbNo()),
                        vndrNmContains(cond.getVndrNm()),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        toLocCdContains(cond.getToLocCd()),
                        statusEq(cond.getStatus()),
                        receiptDtGoe(cond.getDateFrom()),
                        receiptDtLoe(cond.getDateTo())
                )
                .orderBy(openFirst.asc(), lot.expiryDt.asc().nullsLast(), putawayTask.id.asc())
                .fetch();
    }

    /**
     * 미완료(DIRECTED) 지시가 걸려 있는 라인 id. 라인 진행단계({@code IbLine#progressStatus})가
     * 「지시가 나갔는가」를 알아야 해서, 라인마다 묻지 않고 한 번에 받아 나눠 준다.
     */
    public Set<Long> openIbLineIds(Collection<Long> ibLineIds) {
        if (ibLineIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(queryFactory
                .select(putawayTask.ibLine.id)
                .distinct()
                .from(putawayTask)
                .where(
                        putawayTask.ibLine.id.in(ibLineIds),
                        putawayTask.status.eq(PutawayTaskStatus.DIRECTED)
                )
                .fetch());
    }

    /** (입고라인, Lot) 배치별 미완료 지시 잔량. 목록에 「미지시 수량」을 붙일 때 한 번에 받는다 */
    public Map<String, Long> openRemainderByBatch() {
        NumberExpression<Long> remainder = putawayTask.drctQty.subtract(putawayTask.cmplQty).sum();
        List<Tuple> rows = queryFactory
                .select(putawayTask.ibLine.id, putawayTask.lot.id, remainder)
                .from(putawayTask)
                .where(putawayTask.status.eq(PutawayTaskStatus.DIRECTED))
                .groupBy(putawayTask.ibLine.id, putawayTask.lot.id)
                .fetch();

        Map<String, Long> byBatch = new HashMap<>();
        for (Tuple row : rows) {
            byBatch.put(batchKey(row.get(putawayTask.ibLine.id), row.get(putawayTask.lot.id)),
                    Objects.requireNonNullElse(row.get(remainder), 0L));
        }
        return byBatch;
    }

    /** 한 배치의 미완료 지시 잔량. 지시 생성이 배치 상한을 검증할 때 건별로 쓴다 */
    public long openQtyOfBatch(Long ibLineId, Long lotId) {
        Long sum = queryFactory
                .select(putawayTask.drctQty.subtract(putawayTask.cmplQty).sum())
                .from(putawayTask)
                .where(
                        putawayTask.ibLine.id.eq(ibLineId),
                        putawayTask.lot.id.eq(lotId),
                        putawayTask.status.eq(PutawayTaskStatus.DIRECTED)
                )
                .fetchOne();
        return Objects.requireNonNullElse(sum, 0L);
    }

    /**
     * 한 배치(입고라인, Lot)가 스테이징에 남긴 잔량 — RECEIVE(+)/ADJUST(−)/MOVE 출고분(−)의 합.
     * <p>
     * inv_hist를 읽지만 이 집계를 쓰는 곳이 적치지시 생성 하나뿐이라 여기에 둔다. inv의 on_hand는
     * (상품, 로케이션, Lot) 단위라 같은 Lot을 공유하는 다른 입고라인의 물량과 섞여 라인 경계를 구분하지 못한다.
     */
    public long stagingQtyOfBatch(Long ibLineId, Long lotId, String stagingLocCd) {
        Long sum = queryFactory
                .select(invHist.qty.sum())
                .from(invHist)
                .innerJoin(invHist.loc, loc)
                .where(
                        invHist.ibLineId.eq(ibLineId),
                        invHist.lot.id.eq(lotId),
                        loc.locCd.eq(stagingLocCd)
                )
                .fetchOne();
        return Objects.requireNonNullElse(sum, 0L);
    }

    /** 배치 키 — 목록 응답과 지시 잔량을 맞물리는 유일한 지점이라 생성 규칙을 여기 한 곳에 둔다 */
    public static String batchKey(Long ibLineId, Long lotId) {
        return ibLineId + ":" + lotId;
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression ibNoContains(String ibNo) {
        return StringUtils.hasText(ibNo) ? ibOrder.ibNo.containsIgnoreCase(ibNo) : null;
    }

    /** 상대처 — 벤더명 또는 점포명 한 칸으로 검색 (IbLineRepositoryImpl과 같은 판정) */
    private BooleanExpression vndrNmContains(String vndrNm) {
        return StringUtils.hasText(vndrNm)
                ? vendor.vndrNm.containsIgnoreCase(vndrNm).or(store.storeNm.containsIgnoreCase(vndrNm))
                : null;
    }

    private BooleanExpression receiptDtGoe(LocalDate dateFrom) {
        return dateFrom != null ? lot.receiptDt.goe(dateFrom) : null;
    }

    private BooleanExpression receiptDtLoe(LocalDate dateTo) {
        return dateTo != null ? lot.receiptDt.loe(dateTo) : null;
    }

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression toLocCdContains(String toLocCd) {
        return StringUtils.hasText(toLocCd) ? loc.locCd.containsIgnoreCase(toLocCd) : null;
    }

    private BooleanExpression statusEq(PutawayTaskStatus status) {
        return status != null ? putawayTask.status.eq(status) : null;
    }
}
