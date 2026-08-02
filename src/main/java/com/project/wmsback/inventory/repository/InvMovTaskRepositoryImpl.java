package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvMovTaskResponse;
import com.project.wmsback.inventory.dto.InvMovTaskSearchCond;
import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.master.entity.QLoc;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.inventory.entity.QInvMovTask.invMovTask;
import static com.project.wmsback.master.entity.QLot.lot;
import static com.project.wmsback.master.entity.QProd.prod;

@RequiredArgsConstructor
public class InvMovTaskRepositoryImpl implements InvMovTaskRepositoryCustom {

    // 한 쿼리에서 loc을 FROM/TO로 두 번 조인하므로 기본 Q인스턴스 대신 별칭 두 개를 쓴다
    private static final QLoc fromLoc = new QLoc("fromLoc");
    private static final QLoc toLoc = new QLoc("toLoc");

    private final JPAQueryFactory queryFactory;

    @Override
    public List<InvMovTaskResponse> search(InvMovTaskSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(InvMovTaskResponse.class,
                        invMovTask.id, invMovTask.invMovNo, invMovTask.movDvsn,
                        prod.prodCd, prod.prodNm,
                        lot.lotNo, lot.expiryDt,
                        fromLoc.locCd, toLoc.locCd,
                        invMovTask.drctQty, invMovTask.cmplQty,
                        invMovTask.drctQty.subtract(invMovTask.cmplQty),
                        invMovTask.status, invMovTask.createdAt, invMovTask.cmplDt))
                .from(invMovTask)
                .innerJoin(invMovTask.prod, prod)
                .innerJoin(invMovTask.lot, lot)
                .innerJoin(invMovTask.fromLoc, fromLoc)
                .innerJoin(invMovTask.toLoc, toLoc)
                .where(
                        invMovNoContains(cond.getInvMovNo()),
                        movDvsnEq(cond.getMovDvsn()),
                        prodCdContains(cond.getProdCd()),
                        prodNmContains(cond.getProdNm()),
                        fromLocCdContains(cond.getFromLocCd()),
                        toLocCdContains(cond.getToLocCd()),
                        statusEq(cond.getStatus())
                )
                .orderBy(invMovTask.id.desc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression invMovNoContains(String invMovNo) {
        return StringUtils.hasText(invMovNo) ? invMovTask.invMovNo.containsIgnoreCase(invMovNo) : null;
    }

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression prodNmContains(String prodNm) {
        return StringUtils.hasText(prodNm) ? prod.prodNm.containsIgnoreCase(prodNm) : null;
    }

    private BooleanExpression fromLocCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? fromLoc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression toLocCdContains(String locCd) {
        return StringUtils.hasText(locCd) ? toLoc.locCd.containsIgnoreCase(locCd) : null;
    }

    private BooleanExpression statusEq(InvMovStatus status) {
        return status != null ? invMovTask.status.eq(status) : null;
    }

    private BooleanExpression movDvsnEq(InvMovDvsn movDvsn) {
        return movDvsn != null ? invMovTask.movDvsn.eq(movDvsn) : null;
    }
}
