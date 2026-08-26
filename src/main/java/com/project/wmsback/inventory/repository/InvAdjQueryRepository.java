package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvAdjHldTargetResponse;
import com.project.wmsback.inventory.dto.InvAdjTargetResponse;
import com.project.wmsback.inventory.dto.InvAdjTargetSearchCond;
import com.project.wmsback.inventory.entity.InvHldStatus;
import com.project.wmsback.warehouse.entity.LocTyp;
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
import static com.project.wmsback.inventory.entity.QInvHld.invHld;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.wmsback.warehouse.entity.QLot.lot;
import static com.project.wmsback.warehouse.entity.QZon.zon;

/**
 * 재고조정 대상 조회 (재고조정 화면 상단의 두 탭).
 *
 * Spring Data 인터페이스 없이 JPAQueryFactory만 드는 읽기 전용 조회 포트다
 * (InvLotChngQueryRepository와 같은 형태) — 조회 축이 inv 행·inv_hld 건이라 inv_adj 삼각형과 다르다.
 *
 * 불변 전제가 여기서 강제된다 (조건과 무관하게 — 서비스도 저장 시 같은 조건을 재검증한다):
 * - 보관(STORAGE) 로케이션만 — 스테이징은 적치·출고확정이 소진 중이라 대상이 아니다
 * - 보류 라인 대상은 미해제 잔량이 남은 HELD 건만
 *
 * 가용수량 0인 재고 행을 <b>거르지 않는 것</b>이 로트변경 대상 조회와 갈리는 지점이다 —
 * 조정은 (+) 방향이 있어 예약·보류로 가용이 0인 재고도 정당한 대상이다.
 */
@Repository
@RequiredArgsConstructor
public class InvAdjQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** 가용 라인 대상 — 보관 로케이션의 재고 행 */
    public List<InvAdjTargetResponse> searchTargets(InvAdjTargetSearchCond cond) {
        NumberExpression<Long> avalQty = InvQueryExpressions.avalQty();

        return queryFactory
                .select(Projections.constructor(InvAdjTargetResponse.class,
                        prod.id, prod.prodCd, prod.prodNm,
                        loc.id, loc.locCd, lot.id, lot.lotNo, lot.expiryDt,
                        inv.onHandQty, inv.alocQty, inv.hldQty, avalQty))
                .from(inv)
                .innerJoin(inv.prod, prod)
                .innerJoin(inv.loc, loc)
                .innerJoin(inv.lot, lot)
                // 로케이션의 존은 FK가 없어 미등록 존이 있을 수 있다 — leftJoin이라야 그 로케이션의
                // 재고가 조건 없는 조회에서 통째로 사라지지 않는다 (AlocQueryRepository와 같은 판단)
                .leftJoin(loc.zon, zon)
                .where(
                        loc.locTyp.eq(LocTyp.STORAGE),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        locCdContains(cond.getLocCd()),
                        lotNoContains(cond.getLotNo()),
                        zonCdEq(cond.getZonCd())
                )
                .orderBy(prod.prodCd.asc(), loc.locCd.asc(), lot.expiryDt.asc().nullsLast())
                .fetch();
    }

    /**
     * 보류 라인 대상 — 미해제 잔량이 남은 보류 건. 행 단위가 보류 「건」인 것이 위 조회와 다른 지점이고,
     * 그것이 조정이 어느 보류에서 빼는지를 결정론적으로 만드는 근거다.
     *
     * <p>재고 행은 조정전수량(onHandQty)을 얻기 위해 조인한다 — FK가 없어 명시 on 조건으로 잇는다.
     * 보류 잔량이 있으면 그 재고 행은 반드시 존재하므로(ck_inv_qty: hld ≤ onHand) inner join이다.
     */
    public List<InvAdjHldTargetResponse> searchHldTargets(InvAdjTargetSearchCond cond) {
        NumberExpression<Long> remainingQty = invHld.hldQty.subtract(invHld.rlzQty);

        return queryFactory
                .select(Projections.constructor(InvAdjHldTargetResponse.class,
                        invHld.id, invHld.hldNo,
                        prod.id, prod.prodCd, prod.prodNm,
                        loc.id, loc.locCd, lot.id, lot.lotNo, lot.expiryDt,
                        inv.onHandQty, invHld.hldQty, invHld.rlzQty, remainingQty,
                        invHld.rsnCd, invHld.rsnDscr, invHld.createdAt))
                .from(invHld)
                .innerJoin(invHld.prod, prod)
                .innerJoin(invHld.loc, loc)
                .innerJoin(invHld.lot, lot)
                // 로케이션의 존은 FK가 없어 미등록 존이 있을 수 있다 — leftJoin이라야 그 로케이션의
                // 재고가 조건 없는 조회에서 통째로 사라지지 않는다 (AlocQueryRepository와 같은 판단)
                .leftJoin(loc.zon, zon)
                .innerJoin(inv).on(
                        inv.prod.eq(invHld.prod),
                        inv.loc.eq(invHld.loc),
                        inv.lot.eq(invHld.lot))
                .where(
                        invHld.status.eq(InvHldStatus.HELD),
                        remainingQty.gt(0L),
                        loc.locTyp.eq(LocTyp.STORAGE),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        locCdContains(cond.getLocCd()),
                        lotNoContains(cond.getLotNo()),
                        zonCdEq(cond.getZonCd()),
                        hldRsnCdEq(cond.getRsnCd())
                )
                .orderBy(prod.prodCd.asc(), loc.locCd.asc(), invHld.id.asc())
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

    private BooleanExpression lotNoContains(String lotNo) {
        return StringUtils.hasText(lotNo) ? lot.lotNo.containsIgnoreCase(lotNo) : null;
    }

    /** 존 코드 정확일치 — 반품존만 보기(폐기 대상 추리기)가 이 조건의 주 사용처다 */
    private BooleanExpression zonCdEq(String zonCd) {
        return StringUtils.hasText(zonCd) ? zon.zonCd.eq(zonCd) : null;
    }

    /** 보류사유 (HLD_RSN) — 보류 라인 대상에만 적용된다 */
    private BooleanExpression hldRsnCdEq(String rsnCd) {
        return StringUtils.hasText(rsnCd) ? invHld.rsnCd.eq(rsnCd) : null;
    }
}
