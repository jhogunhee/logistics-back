package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.ProdSearchCond;
import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.master.entity.TempZone;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.project.omsback.inbound.entity.QOmsIbLine.omsIbLine;
import static com.project.wmsback.inbound.entity.QIbLine.ibLine;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.master.entity.QLot.lot;
import static com.project.wmsback.master.entity.QProd.prod;
import static com.project.wmsback.master.entity.QProdUom.prodUom;
import static com.project.wmsback.outbound.entity.QOutbLine.outbLine;

@RequiredArgsConstructor
public class ProdRepositoryImpl implements ProdRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Prod> findByIdForUpdate(Long id) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(prod)
                        .where(prod.id.eq(id))
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .fetchOne());
    }

    /**
     * 목록 조회. 포장을 fetch join으로 함께 읽는다 — ProdResponse가 uoms를 항상 훑으므로
     * 없으면 상품 수만큼 추가 쿼리가 나간다. 조인으로 행이 부풀므로 distinct가 필요하다.
     */
    @Override
    public List<Prod> search(ProdSearchCond cond) {
        return queryFactory
                .selectFrom(prod).distinct()
                .leftJoin(prod.uoms, prodUom).fetchJoin()
                .where(
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        tempZoneEq(cond.getTmpZon())
                )
                .orderBy(prod.id.asc())
                .fetch();
    }

    /**
     * 참조가 하나라도 나오면 그 자리에서 이름을 돌려준다 — 몇 건인지는 필요 없고, 삭제를 막을
     * 이유 하나면 충분하다. 순서는 사용자가 납득하기 쉬운 쪽부터다(재고 → 이력 → 문서 → Lot).
     */
    @Override
    public String findFirstReference(Long prodId) {
        if (exists(inv.prod.id.eq(prodId), inv)) return "재고";
        if (exists(invHist.prod.id.eq(prodId), invHist)) return "재고 이력";
        if (exists(omsIbLine.prod.id.eq(prodId), omsIbLine)) return "입고주문";
        if (exists(ibLine.prod.id.eq(prodId), ibLine)) return "입고예정(ASN)";
        if (exists(outbLine.prod.id.eq(prodId), outbLine)) return "출고주문";
        if (exists(lot.prod.id.eq(prodId), lot)) return "Lot";
        return null;
    }

    private boolean exists(BooleanExpression where, EntityPath<?> from) {
        return queryFactory.selectOne().from(from).where(where).fetchFirst() != null;
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression tempZoneEq(TempZone tmpZon) {
        return tmpZon != null ? prod.tmpZon.eq(tmpZon) : null;
    }
}