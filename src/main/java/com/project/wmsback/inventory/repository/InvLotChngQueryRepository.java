package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvLotChngTargetResponse;
import com.project.wmsback.inventory.dto.InvLotChngTargetSearchCond;
import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.entity.QLot;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.mdm.prod.entity.QProd.prod;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;

/**
 * 로트변경 대상 재고 행 조회 (재고 로트변경 화면 상단).
 *
 * Spring Data 인터페이스 없이 JPAQueryFactory만 드는 읽기 전용 조회 포트다
 * (LotAttrQueryRepository와 같은 형태) — 조회 축이 inv 행이고 inv_lot_chng 삼각형(실적 조회)과
 * 축이 달라 어느 한쪽에 넣기 어렵다.
 *
 * 불변 전제가 여기서 강제된다 (조건과 무관하게 — 서비스도 저장 시 같은 조건을 재검증한다):
 * - 보관(STORAGE) 로케이션만 — 스테이징은 적치 잔량 집계가 깨져 대상이 아니다
 * - 유통기한 관리 상품만 — 미관리 상품의 Lot은 두 날짜가 항상 NULL인 것이 정의
 * - 가용수량 > 0 — 로트변경은 가용분만 옮길 수 있다
 */
@Repository
@RequiredArgsConstructor
public class InvLotChngQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<InvLotChngTargetResponse> searchTargets(InvLotChngTargetSearchCond cond) {
        NumberExpression<Long> avalQty = InvQueryExpressions.avalQty();

        return queryFactory
                .select(Projections.constructor(InvLotChngTargetResponse.class,
                        inv.id, prod.prodCd, prod.prodNm, loc.locCd, lot.lotNo,
                        lot.receiptDt, lot.mfgDt, lot.expiryDt, prod.shelfLifeDays,
                        inv.onHandQty, inv.alocQty, inv.hldQty, avalQty))
                .from(inv)
                .innerJoin(inv.prod, prod)
                .innerJoin(inv.loc, loc)
                .innerJoin(inv.lot, lot)
                .where(
                        loc.locTyp.eq(LocTyp.STORAGE),
                        prod.shelfLifeDays.isNotNull(),
                        avalQty.gt(0L),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        lotNoContains(cond.getLotNo()),
                        locCdContains(cond.getLocCd()),
                        tmpZonEq(cond.getTmpZon())
                )
                .orderBy(prod.prodCd.asc(), lot.expiryDt.asc(), loc.locCd.asc())
                .fetch();
    }

    /**
     * 특정 재고 행의 목적지 배치 후보 Lot 조회 (화면의 목적지 선택 모달용).
     * 후보 = 원 Lot과 <b>같은 상품 + 같은 입고일자</b>인 다른 Lot — 배치 재사용 키의 성질상
     * 로트변경이 도달할 수 있는 목적지는 이 집합뿐이다(입고일자는 Lot의 정체성이라 못 바꾼다).
     * 규칙을 화면이 아니라 서버가 강제한다 (대상 조회의 STORAGE·관리 상품 강제와 같은 판단).
     * 제조일자 null인 Lot(관리 전환 전 생성분)은 배치 키가 정의되지 않아 제외한다.
     */
    public List<Lot> searchTargetLots(Long invId) {
        QLot cand = new QLot("cand");
        return queryFactory
                .select(cand)
                .from(inv)
                .innerJoin(inv.lot, lot)
                .innerJoin(cand).on(
                        cand.prod.eq(inv.prod),
                        cand.receiptDt.eq(lot.receiptDt))
                .where(
                        inv.id.eq(invId),
                        cand.id.ne(lot.id),
                        cand.mfgDt.isNotNull()
                )
                .orderBy(cand.mfgDt.asc(), cand.lotNo.asc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression lotNoContains(String lotNo) {
        return StringUtils.hasText(lotNo) ? lot.lotNo.containsIgnoreCase(lotNo) : null;
    }

    private BooleanExpression locCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? loc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression tmpZonEq(TmpZon tmpZon) {
        return tmpZon != null ? prod.tmpZon.eq(tmpZon) : null;
    }
}
