package com.project.wmsback.worker.repository;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.warehouse.entity.QLoc;
import com.project.wmsback.worker.dto.WrkrAcrstDetailResponse;
import com.project.wmsback.worker.dto.WrkrAcrstGroup;
import com.project.wmsback.worker.dto.WrkrAcrstSearchCond;
import com.project.wmsback.worker.dto.WrkrOptionResponse;
import com.project.wmsback.worker.entity.WrkrWorkTyp;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.mdm.usr.entity.QUsr.usr;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;

/**
 * 작업자 실적 조회 — 요약 · 일자별 추이 · 드릴다운.
 *
 * <p>Spring Data 인터페이스 없이 {@code JPAQueryFactory}만 드는 읽기 전용 조회 포트다
 * (inbound의 {@code PutawayTaskQueryRepository}와 같은 형태). 이 도메인에는 저장이 없다 —
 * 실적은 {@code inv_hist}가 이미 쌓아 둔 것을 읽기만 한다.
 *
 * <p>네 조회가 {@link #searchConds}를 함께 쓴다. 그래야 요약의 합계와 드릴다운의 목록 건수가
 * 같은 수를 가리킨다.
 */
@Repository
@RequiredArgsConstructor
public class WrkrAcrstQueryRepository {

    /** 인증 없이 도는 실행(스케줄러)의 작성자. 사람의 실적이 아니라 집계에서 뺀다 */
    private static final String SYSTEM_AUDITOR = "system";

    private final JPAQueryFactory queryFactory;

    /** 작업자 × (tx_typ, rfn_doc_typ) 묶음. 작업 종류로 접는 것은 서비스가 한다 */
    public List<WrkrAcrstGroup> summary(WrkrAcrstSearchCond cond) {
        return queryFactory
                .select(invHist.createdBy, usr.usrNm, invHist.txTyp, invHist.rfnDocTyp,
                        invHist.count(), invHist.qty.abs().sum())
                .from(invHist)
                // 퇴사자 계정은 지워지므로(usr 물리삭제) 이름이 없을 수 있다 — 실적 행은 남아야 한다
                .leftJoin(usr).on(usr.loginId.eq(invHist.createdBy))
                .where(searchConds(cond))
                .groupBy(invHist.createdBy, usr.usrNm, invHist.txTyp, invHist.rfnDocTyp)
                .fetch().stream()
                .map(t -> new WrkrAcrstGroup(null,
                        t.get(invHist.createdBy), t.get(usr.usrNm),
                        t.get(invHist.txTyp), t.get(invHist.rfnDocTyp),
                        nz(t.get(invHist.count())), nz(t.get(invHist.qty.abs().sum()))))
                .toList();
    }

    /**
     * 일자 × (tx_typ, rfn_doc_typ) 묶음.
     *
     * <p>일자를 {@code cast(created_at as date)}로 뽑지 않고 연·월·일 셋으로 쪼개 묶는다 —
     * 그 캐스트는 JDBC가 {@code java.sql.Date}로 돌려줘 {@code LocalDate} 매핑에서 터진다.
     * 연·월·일은 HQL {@code extract}로 내려가 정수로 오므로 그런 함정이 없다.
     */
    public List<WrkrAcrstGroup> daily(WrkrAcrstSearchCond cond) {
        NumberExpression<Integer> year = invHist.createdAt.year();
        NumberExpression<Integer> month = invHist.createdAt.month();
        NumberExpression<Integer> day = invHist.createdAt.dayOfMonth();

        return queryFactory
                .select(year, month, day, invHist.txTyp, invHist.rfnDocTyp,
                        invHist.count(), invHist.qty.abs().sum())
                .from(invHist)
                .where(searchConds(cond))
                .groupBy(year, month, day, invHist.txTyp, invHist.rfnDocTyp)
                .orderBy(year.asc(), month.asc(), day.asc())
                .fetch().stream()
                .map(t -> new WrkrAcrstGroup(
                        LocalDate.of(t.get(year), t.get(month), t.get(day)), null, null,
                        t.get(invHist.txTyp), t.get(invHist.rfnDocTyp),
                        nz(t.get(invHist.count())), nz(t.get(invHist.qty.abs().sum()))))
                .toList();
    }

    /** 기간 안에 실적이 있는 작업자 — 화면 필터의 선택지. 작업자·종류 조건은 일부러 빼고 본다 */
    public List<WrkrOptionResponse> workers(WrkrAcrstSearchCond cond) {
        WrkrAcrstSearchCond periodOnly = new WrkrAcrstSearchCond();
        periodOnly.setDateFrom(cond.getDateFrom());
        periodOnly.setDateTo(cond.getDateTo());

        return queryFactory
                .select(invHist.createdBy, usr.usrNm)
                .from(invHist)
                .leftJoin(usr).on(usr.loginId.eq(invHist.createdBy))
                .where(searchConds(periodOnly))
                .groupBy(invHist.createdBy, usr.usrNm)
                .orderBy(invHist.createdBy.asc())
                .fetch().stream()
                .map(t -> new WrkrOptionResponse(t.get(invHist.createdBy), t.get(usr.usrNm)))
                .toList();
    }

    /** 집계가 센 그 건들의 목록 */
    public PageResponse<WrkrAcrstDetailResponse> detail(WrkrAcrstSearchCond cond, PageCond pageCond) {
        QLoc fromLocAlias = new QLoc("fromLocAlias");
        QLoc toLocAlias = new QLoc("toLocAlias");

        List<WrkrAcrstDetailResponse> rows = queryFactory
                .select(invHist.id, invHist.createdAt, invHist.createdBy, usr.usrNm,
                        invHist.txTyp, invHist.rfnDocTyp,
                        prod.prodCd, prod.prodNm, lot.lotNo, loc.locCd,
                        fromLocAlias.locCd, toLocAlias.locCd,
                        invHist.qty, invHist.rfnDocNo)
                .from(invHist)
                .innerJoin(invHist.prod, prod)
                .innerJoin(invHist.loc, loc)
                .innerJoin(invHist.lot, lot)
                .leftJoin(usr).on(usr.loginId.eq(invHist.createdBy))
                // from_loc_id/to_loc_id는 FK 없는 느슨한 참조라 값으로 직접 붙인다 (재고이력 조회와 같은 형태)
                .leftJoin(fromLocAlias).on(fromLocAlias.id.eq(invHist.fromLocId))
                .leftJoin(toLocAlias).on(toLocAlias.id.eq(invHist.toLocId))
                .where(searchConds(cond))
                // createdAt만으로는 같은 밀리초 행의 순서가 흔들려 페이지 경계에서 행이 겹치거나 빠진다
                .orderBy(invHist.createdAt.desc(), invHist.id.desc())
                .offset(pageCond.getOffset())
                .limit(pageCond.getSize())
                .fetch().stream()
                .map(t -> toDetail(t, fromLocAlias, toLocAlias))
                .toList();

        Long totCnt = queryFactory
                .select(invHist.count())
                .from(invHist)
                .where(searchConds(cond))
                .fetchOne();

        return PageResponse.of(rows, totCnt, pageCond);
    }

    private static WrkrAcrstDetailResponse toDetail(Tuple t, QLoc fromLocAlias, QLoc toLocAlias) {
        return new WrkrAcrstDetailResponse(
                t.get(invHist.id), t.get(invHist.createdAt),
                t.get(invHist.createdBy), t.get(usr.usrNm),
                WrkrWorkTyp.of(t.get(invHist.txTyp), t.get(invHist.rfnDocTyp)),
                t.get(prod.prodCd), t.get(prod.prodNm), t.get(lot.lotNo), t.get(loc.locCd),
                t.get(fromLocAlias.locCd), t.get(toLocAlias.locCd),
                Math.abs(nz(t.get(invHist.qty))), t.get(invHist.rfnDocNo));
    }

    /** 네 조회가 같은 조건을 쓰도록 한자리에 모은다 */
    private BooleanExpression[] searchConds(WrkrAcrstSearchCond cond) {
        return new BooleanExpression[]{
                classified(),
                arrivalLegOnly(),
                invHist.createdBy.ne(SYSTEM_AUDITOR),
                loginIdEq(cond.getLoginId()),
                workTypEq(cond.getWorkTyp()),
                createdAtGoe(cond.getDateFrom()),
                createdAtLt(cond.getDateTo())
        };
    }

    /**
     * 규칙표에 있는 조합만 센다. 분류되지 않는 조합(참조문서 없는 수동 ADJUST 등)은 빼는 편이
     * 낫다 — 종류를 못 정한 채 합계에만 얹히면 어느 칸에도 대응하지 않는 수가 된다.
     */
    private static BooleanExpression classified() {
        return invHist.txTyp.in(TxTyp.RECEIVE, TxTyp.PICK, TxTyp.SHIP, TxTyp.RPLN)
                .or(invHist.txTyp.eq(TxTyp.MOVE)
                        .and(invHist.rfnDocTyp.in(RefDocTyp.INBOUND, RefDocTyp.INV_MOV)))
                .or(invHist.txTyp.eq(TxTyp.ADJUST)
                        .and(invHist.rfnDocTyp.in(RefDocTyp.INBOUND, RefDocTyp.INV_STKTK,
                                RefDocTyp.INV_ADJ, RefDocTyp.LOT_CHNG)));
    }

    /**
     * 실행 1회가 이력 2행인 유형(이동·피킹·보충·로트변경)은 도착 다리(+)만 센다 —
     * 두 다리를 다 세면 적치 한 번이 두 건이 되어 실적이 정확히 두 배가 된다.
     */
    private static BooleanExpression arrivalLegOnly() {
        return pairedLegs().not().or(invHist.qty.gt(0));
    }

    private static BooleanExpression pairedLegs() {
        return invHist.txTyp.in(TxTyp.MOVE, TxTyp.PICK, TxTyp.RPLN)
                .or(invHist.txTyp.eq(TxTyp.ADJUST).and(invHist.rfnDocTyp.eq(RefDocTyp.LOT_CHNG)));
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private static BooleanExpression loginIdEq(String loginId) {
        return StringUtils.hasText(loginId) ? invHist.createdBy.eq(loginId) : null;
    }

    /** 화면이 고른 작업 종류를 원장의 조합 조건으로 되돌린다 ({@link WrkrWorkTyp#of}의 역방향) */
    private static BooleanExpression workTypEq(WrkrWorkTyp workTyp) {
        if (workTyp == null) {
            return null;
        }
        return switch (workTyp) {
            case RECEIVE -> invHist.txTyp.eq(TxTyp.RECEIVE);
            case PICK -> invHist.txTyp.eq(TxTyp.PICK);
            case SHIP -> invHist.txTyp.eq(TxTyp.SHIP);
            case RPLN -> invHist.txTyp.eq(TxTyp.RPLN);
            case PUTAWAY -> invHist.txTyp.eq(TxTyp.MOVE).and(invHist.rfnDocTyp.eq(RefDocTyp.INBOUND));
            case INV_MOV -> invHist.txTyp.eq(TxTyp.MOVE).and(invHist.rfnDocTyp.eq(RefDocTyp.INV_MOV));
            case RECEIVE_CNCL -> invHist.txTyp.eq(TxTyp.ADJUST).and(invHist.rfnDocTyp.eq(RefDocTyp.INBOUND));
            case STKTK -> invHist.txTyp.eq(TxTyp.ADJUST).and(invHist.rfnDocTyp.eq(RefDocTyp.INV_STKTK));
            case INV_ADJ -> invHist.txTyp.eq(TxTyp.ADJUST).and(invHist.rfnDocTyp.eq(RefDocTyp.INV_ADJ));
            case LOT_CHNG -> invHist.txTyp.eq(TxTyp.ADJUST).and(invHist.rfnDocTyp.eq(RefDocTyp.LOT_CHNG));
        };
    }

    // 화면은 날짜만 입력받지만 created_at은 TIMESTAMP이므로 하루 단위 범위로 변환한다
    private static BooleanExpression createdAtGoe(LocalDate dateFrom) {
        return dateFrom != null ? invHist.createdAt.goe(dateFrom.atStartOfDay()) : null;
    }

    private static BooleanExpression createdAtLt(LocalDate dateTo) {
        return dateTo != null ? invHist.createdAt.lt(dateTo.plusDays(1).atStartOfDay()) : null;
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }
}
