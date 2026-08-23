package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.dto.IbOrderCfmResponse;
import com.project.wmsback.inbound.dto.IbOrderInspResponse;
import com.project.wmsback.inbound.dto.IbOrderResponse;
import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbPrgr;
import com.project.wmsback.inbound.entity.IbStatus;
import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import com.project.wmsback.inbound.entity.QIbLine;
import com.project.wmsback.inventory.entity.TxTyp;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.project.wmsback.inbound.entity.QIbLine.ibLine;
import static com.project.wmsback.inbound.entity.QIbOrder.ibOrder;
import static com.project.wmsback.inbound.entity.QPutawayTask.putawayTask;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.mdm.vendor.entity.QVendor.vendor;

/**
 * 입고건 목록을 화면별로 세 벌 뽑는다. 셋 다 목록 1행을 SQL 한 번으로 완성한다 —
 * 라인을 fetch join하지 않으므로 행이 뻥튀기되지 않고, 진행단계도 HAVING으로 거를 수 있다.
 * <p>
 * <b>나눈 기준은 뽑는 컬럼이 아니라 쿼리 모양이다.</b> 컬럼 하나 더 뽑는 건 사실상 공짜지만,
 * 조인·서브쿼리·집계는 그 화면이 안 써도 비용을 문다. 아래 둘이 화면마다 갈린다:
 * <ul>
 *   <li>진행단계 파생 → 적치지시 EXISTS 서브쿼리 ({@code searchForInsp}는 안 쓴다)
 *   <li>최종 검수일시 → inv_hist 중첩 서브쿼리 ({@code searchForCfm}은 안 쓴다)
 * </ul>
 * 공통 조각(진행단계 CASE · 검수일시 · 집계 · 검색조건)은 아래 private 메서드 한 벌을 셋이 공유한다.
 */
@RequiredArgsConstructor
public class IbOrderRepositoryImpl implements IbOrderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /** 입고예정(ASN) 관리 · 대시보드 — 진행단계와 최종 검수일시를 둘 다 쓴다 */
    @Override
    public List<IbOrderResponse> search(IbOrderSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(IbOrderResponse.class,
                        ibOrder.id, ibOrder.ibNo, progressCode(),
                        vendor.vndrNm, ibOrder.expctDe,
                        sumOrZero(ibLine.expctQty),
                        lastReceiveDt(), ibOrder.cfmDt))
                .from(ibOrder)
                .innerJoin(ibOrder.vendor, vendor)
                .leftJoin(ibOrder.lines, ibLine)
                .where(searchConds(cond))
                .groupBy(ibOrder.id, ibOrder.ibNo, ibOrder.status, ibOrder.expctDe,
                        ibOrder.cfmDt, vendor.vndrNm)
                .having(prgrIn(cond.getPrgr()))
                .orderBy(ibOrder.id.desc())
                .fetch();
    }

    /**
     * 입고검수 · 검수정책 시뮬레이션 — 저장 상태와 라인 진행(3/5)만 본다.
     * 진행단계를 안 만들므로 적치지시 EXISTS 서브쿼리가 없다.
     * <p>
     * 같은 이유로 {@code cond.prgr}는 여기서 무시된다 — 그 필터를 받으려면 결국 EXISTS가 되살아난다.
     */
    @Override
    public List<IbOrderInspResponse> searchForInsp(IbOrderSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(IbOrderInspResponse.class,
                        ibOrder.id, ibOrder.ibNo, ibOrder.status,
                        vendor.vndrNm, ibOrder.expctDe,
                        ibLine.id.count().intValue(), cmplLineCount(),
                        lastReceiveDt()))
                .from(ibOrder)
                .innerJoin(ibOrder.vendor, vendor)
                .leftJoin(ibOrder.lines, ibLine)
                .where(searchConds(cond))
                .groupBy(ibOrder.id, ibOrder.ibNo, ibOrder.status, ibOrder.expctDe, vendor.vndrNm)
                .orderBy(ibOrder.id.desc())
                .fetch();
    }

    /**
     * 입고확정 — 진행단계와 수량 합계로 결품·미적치를 보여준다.
     * 최종 검수일시를 안 만들므로 inv_hist 중첩 서브쿼리가 없다.
     */
    @Override
    public List<IbOrderCfmResponse> searchForCfm(IbOrderSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(IbOrderCfmResponse.class,
                        ibOrder.id, ibOrder.ibNo, progressCode(), ibOrder.status,
                        vendor.vndrNm, ibOrder.expctDe,
                        sumOrZero(ibLine.expctQty), sumOrZero(ibLine.rcvdQty), sumOrZero(ibLine.ptawyQty),
                        ibOrder.cfmDt))
                .from(ibOrder)
                .innerJoin(ibOrder.vendor, vendor)
                .leftJoin(ibOrder.lines, ibLine)
                .where(searchConds(cond))
                .groupBy(ibOrder.id, ibOrder.ibNo, ibOrder.status, ibOrder.expctDe,
                        ibOrder.cfmDt, vendor.vndrNm)
                .having(prgrIn(cond.getPrgr()))
                .orderBy(ibOrder.id.desc())
                .fetch();
    }

    /** 셋이 공유하는 검색조건. 각 조건은 값이 없으면 null이라 where()가 알아서 무시한다 */
    private Predicate[] searchConds(IbOrderSearchCond cond) {
        return new Predicate[]{
                ibNoContains(cond.getIbNo()),
                vndrNmContains(cond.getVndrNm()),
                expctDeGoe(cond.getDateFrom()),
                expctDeLoe(cond.getDateTo())
        };
    }

    /**
     * 화면 표시용 5단계 진행({@link IbPrgr}) — 저장하지 않고 상태 · 라인 수량 · 적치지시 존재에서 파생한다.
     * 판정 순서는 {@code IbLine#progressStatus}와 같은 어휘를 헤더 단위로 옮긴 것이다.
     * <p>
     * SCHEDULED 판정(Σrcvd = 0)이 적치완료 판정보다 먼저다 — 검수가 하나도 없으면
     * 전 라인이 0 = 0으로 「전량 적치」를 헛통과하기 때문.
     * <p>
     * <b>enum이 아니라 이름 문자열을 만든다.</b> {@code then(IbPrgr.X)}로 enum을 넣으면 그 값이
     * 바인딩 파라미터로 나가는데, IbPrgr는 어느 컬럼에도 매핑되지 않아 Hibernate가 타입을 정하지 못하고
     * {@code Could not determine ValueMapping for SqmParameter}로 죽는다. 같은 자리에 문자열이나
     * 숫자를 넣으면 Hibernate가 타입을 추론하므로 이름으로 받아 응답 DTO 생성자가 enum으로 되돌린다.
     */
    private StringExpression progressCode() {
        return new CaseBuilder()
                .when(ibOrder.status.eq(IbStatus.CONFIRMED)).then(IbPrgr.CONFIRMED.name())
                // 라인이 없으면 이 합이 0이라 여기서 걸린다 (빈 라인 목록의 Java 합계와 같다)
                .when(sumOrZero(ibLine.rcvdQty).eq(0L)).then(IbPrgr.SCHEDULED.name())
                .when(notFullyPutawayLines().eq(0)).then(IbPrgr.PTAWY_CMPL.name())
                .when(hasOpenPtawyDrct().or(sumOrZero(ibLine.ptawyQty).gt(0L))).then(IbPrgr.PTAWY_DRCT.name())
                .otherwise(IbPrgr.RECEIVING.name());
    }

    /** 적치가 덜 된 라인 수 — 0이면 전량 적치(IbOrder#allLinesFullyPutaway와 같은 판정) */
    private NumberExpression<Integer> notFullyPutawayLines() {
        return new CaseBuilder()
                .when(ibLine.ptawyQty.ne(ibLine.rcvdQty)).then(1).otherwise(0).sum();
    }

    /** 미완료(DIRECTED) 적치지시가 걸려 있는가 — 「검수」와 「적치지시」를 가르는 유일한 재료 */
    private BooleanExpression hasOpenPtawyDrct() {
        QIbLine taskLine = new QIbLine("taskLine");
        return JPAExpressions
                .selectOne()
                .from(putawayTask)
                .innerJoin(putawayTask.ibLine, taskLine)
                .where(
                        taskLine.ibOrder.id.eq(ibOrder.id),
                        putawayTask.status.eq(PutawayTaskStatus.DIRECTED)
                )
                .exists();
    }

    /**
     * 최종 검수일시 — 이 입고건 라인들의 inv_hist RECEIVE 행 중 가장 늦은 created_at.
     * <p>
     * 최초가 아니라 최종인 이유: 최초는 한 번 찍히면 갱신되지 않아 「착수했다」만 말한다.
     * 라인이 여럿이면 첫 라인 하나만 반영되고 나머지가 나중에 검수돼도 값이 그대로다.
     * 최종은 계속 갱신되므로 「마지막으로 움직인 때」를 말하고 전량검수 완료 시점과도 맞는다.
     * <p>
     * ib_order에 컬럼을 두지 않고 원장에서 파생하는 이유: 검수는 이미 inv_hist에 RECEIVE 행을
     * 남기며 ib_line_id를 채우므로 원천이 있고, 캐시 컬럼을 늘리면 갱신 누락 지점이 하나 는다.
     * <p>
     * inv_hist.ib_line_id는 FK도 연관관계도 아닌 스칼라라 조인이 안 된다 — 이 입고건의 라인 id를
     * 뽑는 서브쿼리를 한 겹 더 두고 그 컬럼으로 직접 거른다.
     */
    private Expression<LocalDateTime> lastReceiveDt() {
        QIbLine inspLine = new QIbLine("inspLine");
        return JPAExpressions
                .select(invHist.createdAt.max())
                .from(invHist)
                .where(
                        invHist.ibLineId.in(JPAExpressions
                                .select(inspLine.id)
                                .from(inspLine)
                                .where(inspLine.ibOrder.id.eq(ibOrder.id))),
                        invHist.txTyp.eq(TxTyp.RECEIVE)
                );
    }

    /** 전량 검수된 라인 수 — 라인이 없으면 CASE가 ELSE로 떨어져 0이 된다(null 아님) */
    private NumberExpression<Integer> cmplLineCount() {
        return new CaseBuilder()
                .when(ibLine.rcvdQty.goe(ibLine.expctQty)).then(1).otherwise(0).sum();
    }

    /**
     * 라인이 하나도 없으면 SUM이 null이라, 그대로 두면 응답 조립의 언박싱에서 터진다.
     * {@code Coalesce#asNumber}는 와일드카드 타입이라 비교에 못 써서 템플릿으로 쓴다.
     */
    private NumberExpression<Long> sumOrZero(NumberPath<Long> qty) {
        return Expressions.numberTemplate(Long.class, "coalesce({0}, 0)", qty.sum());
    }

    // 조건 메서드가 null을 반환하면 where()·having()이 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    /**
     * 진행단계 필터. 조건이 있으면 CASE 식 전체가 having에 한 번 더 펼쳐진다
     * — SQL은 select 별칭을 having에서 못 쓴다.
     */
    private BooleanExpression prgrIn(List<IbPrgr> prgr) {
        return prgr != null && !prgr.isEmpty()
                ? progressCode().in(prgr.stream().map(IbPrgr::name).toList())
                : null;
    }

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
