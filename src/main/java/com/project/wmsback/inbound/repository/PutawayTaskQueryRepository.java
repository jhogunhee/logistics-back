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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.project.wmsback.inbound.entity.QIbLine.ibLine;
import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;
import static com.project.wmsback.inbound.entity.QPutawayTask.putawayTask;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.mdm.prod.entity.QProd.prod;

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
                        putawayTask.id, ibLine.id, ibOrder.ibNo,
                        prod.prodCd, prod.prodNm, prod.tmpZon,
                        lot.id, lot.lotNo, lot.expiryDt,
                        loc.id, loc.locCd,
                        putawayTask.drctQty, putawayTask.cmplQty,
                        putawayTask.drctQty.subtract(putawayTask.cmplQty),
                        putawayTask.status, putawayTask.createdAt, putawayTask.cmplDt))
                .from(putawayTask)
                .innerJoin(putawayTask.ibLine, ibLine)
                .innerJoin(ibLine.ibOrder, ibOrder)
                .innerJoin(ibLine.prod, prod)
                .innerJoin(putawayTask.lot, lot)
                .innerJoin(putawayTask.toLoc, loc)
                .where(
                        ibNoContains(cond.getIbNo()),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        toLocCdContains(cond.getToLocCd()),
                        statusEq(cond.getStatus())
                )
                .orderBy(openFirst.asc(), lot.expiryDt.asc().nullsLast(), putawayTask.id.asc())
                .fetch();
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
