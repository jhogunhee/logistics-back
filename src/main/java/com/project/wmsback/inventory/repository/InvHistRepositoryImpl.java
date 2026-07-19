package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvHistResponse;
import com.project.wmsback.inventory.dto.InvHistSearchCond;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.TxType;
import com.project.wmsback.master.entity.QLoc;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.master.entity.QLoc.loc;
import static com.project.wmsback.master.entity.QLot.lot;
import static com.project.wmsback.master.entity.QSku.sku;

@RequiredArgsConstructor
public class InvHistRepositoryImpl implements InvHistRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<InvHist> findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(Long ibLineId, TxType txType) {
        return queryFactory
                .selectFrom(invHist)
                .where(invHist.ibLineId.eq(ibLineId), invHist.txType.eq(txType))
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
                        invHist.id, invHist.txType,
                        sku.skuCd, sku.skuNm,
                        loc.locCd, loc.zoneCd, loc.tempZone,
                        lot.lotNo,
                        invHist.qty,
                        invHist.refDocType, invHist.refDocNo,
                        fromLocAlias.locCd, toLocAlias.locCd,
                        invHist.createdBy, invHist.createdAt))
                .from(invHist)
                .innerJoin(invHist.sku, sku)
                .innerJoin(invHist.loc, loc)
                .innerJoin(invHist.lot, lot)
                // from_loc_id/to_loc_id는 FK 없는 느슨한 참조라 연관관계 조인이 아니라 값으로 직접 붙인다
                .leftJoin(fromLocAlias).on(fromLocAlias.id.eq(invHist.fromLocId))
                .leftJoin(toLocAlias).on(toLocAlias.id.eq(invHist.toLocId))
                .where(
                        skuCdContains(cond.getSkuCd()),
                        skuNmContains(cond.getSkuNm()),
                        locCdContains(cond.getLocCd()),
                        txTypeEq(cond.getTxType()),
                        refDocNoContains(cond.getRefDocNo()),
                        createdAtGoe(cond.getDateFrom()),
                        createdAtLt(cond.getDateTo())
                )
                .orderBy(invHist.createdAt.desc(), invHist.id.desc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression skuCdContains(String skuCd) {
        return StringUtils.hasText(skuCd) ? sku.skuCd.containsIgnoreCase(skuCd) : null;
    }

    private BooleanExpression skuNmContains(String skuNm) {
        return StringUtils.hasText(skuNm) ? sku.skuNm.containsIgnoreCase(skuNm) : null;
    }

    private BooleanExpression locCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? loc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression txTypeEq(TxType txType) {
        return txType != null ? invHist.txType.eq(txType) : null;
    }

    private BooleanExpression refDocNoContains(String refDocNo) {
        return StringUtils.hasText(refDocNo) ? invHist.refDocNo.containsIgnoreCase(refDocNo) : null;
    }

    // 화면은 날짜만 입력받지만 created_at은 TIMESTAMP이므로 하루 단위 범위로 변환한다
    private BooleanExpression createdAtGoe(LocalDate dateFrom) {
        return dateFrom != null ? invHist.createdAt.goe(dateFrom.atStartOfDay()) : null;
    }

    private BooleanExpression createdAtLt(LocalDate dateTo) {
        return dateTo != null ? invHist.createdAt.lt(dateTo.plusDays(1).atStartOfDay()) : null;
    }
}
