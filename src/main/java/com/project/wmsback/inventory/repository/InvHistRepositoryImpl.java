package com.project.wmsback.inventory.repository;

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
    public List<InvHistResponse> search(InvHistSearchCond cond) {
        // MOVE는 from_loc/to_loc가 양쪽 다리 모두에 채워져 있어 조인만 붙이면 된다 (상대 건을 다시 찾을 필요 없음)
        QLoc fromLocAlias = new QLoc("fromLocAlias");
        QLoc toLocAlias = new QLoc("toLocAlias");

        return queryFactory
                .select(Projections.constructor(InvHistResponse.class,
                        invHist.id, invHist.txTyp,
                        prod.prodCd, prod.prodNm,
                        loc.locCd, loc.zonCd, loc.tmpZon,
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
                .where(
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        locCdContains(cond.getLocCd()),
                        txTypEq(cond.getTxTyp()),
                        rfnDocNoContains(cond.getRfnDocNo()),
                        createdAtGoe(cond.getDateFrom()),
                        createdAtLt(cond.getDateTo())
                )
                .orderBy(invHist.createdAt.desc(), invHist.id.desc())
                .fetch();
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
