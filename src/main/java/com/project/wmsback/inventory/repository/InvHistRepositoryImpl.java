package com.project.wmsback.inventory.repository;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.inventory.dto.InvHistResponse;
import com.project.wmsback.inventory.dto.InvHistSearchCond;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.wmsback.warehouse.entity.QLoc;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.mdm.prod.entity.QProd.prod;

@RequiredArgsConstructor
public class InvHistRepositoryImpl implements InvHistRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<InvHist> findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(Long ibLineId, TxTyp txTyp) {
        return queryFactory
                .selectFrom(invHist)
                .where(invHist.ibLineId.eq(ibLineId), invHist.txTyp.eq(txTyp))
                .orderBy(invHist.createdAt.desc())
                .fetch();
    }

    @Override
    public List<InvHist> findAllByIbLineIdInAndTxTypeOrderByCreatedAtDesc(Collection<Long> ibLineIds, TxTyp txTyp) {
        if (ibLineIds.isEmpty()) {
            return List.of();
        }
        // 상품과 Lot은 응답이 바로 쓰므로 fetch join으로 함께 로딩한다 (행마다 다시 조회하지 않게)
        return queryFactory
                .selectFrom(invHist)
                .join(invHist.prod, prod).fetchJoin()
                .join(invHist.lot, lot).fetchJoin()
                .where(invHist.ibLineId.in(ibLineIds), invHist.txTyp.eq(txTyp))
                .orderBy(invHist.createdAt.desc())
                .fetch();
    }

    @Override
    public PageResponse<InvHistResponse> search(InvHistSearchCond cond, PageCond pageCond) {
        // MOVE는 from_loc/to_loc가 양쪽 다리 모두에 채워져 있어 조인만 붙이면 된다 (상대 건을 다시 찾을 필요 없음)
        QLoc fromLocAlias = new QLoc("fromLocAlias");
        QLoc toLocAlias = new QLoc("toLocAlias");

        List<InvHistResponse> rows = queryFactory
                .select(Projections.constructor(InvHistResponse.class,
                        invHist.id, invHist.txTyp,
                        prod.prodCd, prod.prodNm,
                        // 온도대는 상품의 것이다 — 로케이션 온도대를 실으면 같은 상품의 이력이
                        // 거쳐 간 존마다 다른 온도대로 보인다(스테이징은 DRY, 피킹존은 CHL).
                        // 현재고 조회를 비롯한 다른 재고 화면도 전부 prod.tmp_zon을 쓴다
                        loc.locCd, loc.zon.zonCd, prod.tmpZon,
                        lot.lotNo,
                        invHist.qty,
                        invHist.rfnDocTyp, invHist.rfnDocNo,
                        fromLocAlias.locCd, toLocAlias.locCd,
                        invHist.createdBy, invHist.createdAt))
                .from(invHist)
                .innerJoin(invHist.prod, prod)
                .innerJoin(invHist.loc, loc)
                .innerJoin(invHist.lot, lot)
                // from_loc_id/to_loc_id는 FK 없는 느슨한 참조라 연관관계 조인이 아니라 값으로 직접 붙인다
                .leftJoin(fromLocAlias).on(fromLocAlias.id.eq(invHist.fromLocId))
                .leftJoin(toLocAlias).on(toLocAlias.id.eq(invHist.toLocId))
                .where(searchConds(cond))
                // createdAt만으로는 같은 밀리초 행의 순서가 흔들려 페이지 경계에서 행이 겹치거나 빠진다
                .orderBy(invHist.createdAt.desc(), invHist.id.desc())
                .offset(pageCond.getOffset())
                .limit(pageCond.getSize())
                .fetch();

        // 셈에는 MOVE 짝 조인이 필요 없다 — 조건이 걸리는 것은 상품·로케이션·Lot뿐이다
        Long totCnt = queryFactory
                .select(invHist.count())
                .from(invHist)
                .innerJoin(invHist.prod, prod)
                .innerJoin(invHist.loc, loc)
                .innerJoin(invHist.lot, lot)
                .where(searchConds(cond))
                .fetchOne();

        return PageResponse.of(rows, totCnt, pageCond);
    }

    /** 목록과 셈이 같은 조건을 쓰도록 한자리에 모은다 */
    private BooleanExpression[] searchConds(InvHistSearchCond cond) {
        return new BooleanExpression[]{
                prodCdContains(cond.getProdCd()),
                prodNmContains(cond.getProdNm()),
                locCdContains(cond.getLocCd()),
                lotNoContains(cond.getLotNo()),
                txTypEq(cond.getTxTyp()),
                rfnDocNoContains(cond.getRfnDocNo()),
                createdAtGoe(cond.getDateFrom()),
                createdAtLt(cond.getDateTo())
        };
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression locCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? loc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression lotNoContains(String lotNo) {
        return StringUtils.hasText(lotNo) ? lot.lotNo.containsIgnoreCase(lotNo) : null;
    }

    private BooleanExpression txTypEq(TxTyp txTyp) {
        return txTyp != null ? invHist.txTyp.eq(txTyp) : null;
    }

    private BooleanExpression rfnDocNoContains(String rfnDocNo) {
        return StringUtils.hasText(rfnDocNo) ? invHist.rfnDocNo.containsIgnoreCase(rfnDocNo) : null;
    }

    // 화면은 날짜만 입력받지만 created_at은 TIMESTAMP이므로 하루 단위 범위로 변환한다
    private BooleanExpression createdAtGoe(LocalDate dateFrom) {
        return dateFrom != null ? invHist.createdAt.goe(dateFrom.atStartOfDay()) : null;
    }

    private BooleanExpression createdAtLt(LocalDate dateTo) {
        return dateTo != null ? invHist.createdAt.lt(dateTo.plusDays(1).atStartOfDay()) : null;
    }
}
