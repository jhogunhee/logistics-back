package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvStktkResponse;
import com.project.wmsback.inventory.dto.InvStktkSearchCond;
import com.project.wmsback.inventory.entity.InvStktkStatus;
import com.project.wmsback.inventory.entity.QInvStktkLn;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.project.wmsback.inventory.entity.QInvStktk.invStktk;
import static com.project.wmsback.warehouse.entity.QLoc.loc;
import static com.project.mdm.prod.entity.QProd.prod;

@RequiredArgsConstructor
public class InvStktkRepositoryImpl implements InvStktkRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 라인 수·입력 라인 수를 각각 상관 서브쿼리로 센다 — 같은 테이블을 두 번 쓰므로 별칭을 나눈다
    private static final QInvStktkLn LN_ALL = new QInvStktkLn("lnAll");
    private static final QInvStktkLn LN_CNTD = new QInvStktkLn("lnCntd");

    @Override
    public List<InvStktkResponse> search(InvStktkSearchCond cond) {
        return queryFactory
                .select(Projections.constructor(InvStktkResponse.class,
                        invStktk.id, invStktk.stktkNo,
                        invStktk.zonCd, loc.locCd, prod.prodCd, prod.prodNm,
                        invStktk.status,
                        JPAExpressions.select(LN_ALL.count()).from(LN_ALL)
                                .where(LN_ALL.invStktk.id.eq(invStktk.id)),
                        JPAExpressions.select(LN_CNTD.count()).from(LN_CNTD)
                                .where(LN_CNTD.invStktk.id.eq(invStktk.id), LN_CNTD.stktkQty.isNotNull()),
                        invStktk.createdAt, invStktk.cfmDt))
                .from(invStktk)
                // 범위 지정은 선택이라 둘 다 left join이다 (조건 없는 전 창고 조사가 있다)
                .leftJoin(invStktk.loc, loc)
                .leftJoin(invStktk.prod, prod)
                .where(
                        stktkNoContains(cond.getStktkNo()),
                        statusIn(cond.getStatus()),
                        zonCdEq(cond.getZonCd()),
                        prodCdContains(cond.getProdCd()),
                        createdFrom(cond.getFromDe()),
                        createdTo(cond.getToDe())
                )
                .orderBy(invStktk.id.desc())
                .fetch();
    }

    // 조건 메서드가 null을 반환하면 where()가 그 조건을 무시한다 — QueryDSL 동적 쿼리 관례

    private BooleanExpression stktkNoContains(String stktkNo) {
        return StringUtils.hasText(stktkNo) ? invStktk.stktkNo.containsIgnoreCase(stktkNo) : null;
    }

    private BooleanExpression statusIn(List<InvStktkStatus> status) {
        return status != null && !status.isEmpty() ? invStktk.status.in(status) : null;
    }

    private BooleanExpression zonCdEq(String zonCd) {
        return StringUtils.hasText(zonCd) ? invStktk.zonCd.eq(zonCd) : null;
    }

    private BooleanExpression prodCdContains(String prodCd) {
        return StringUtils.hasText(prodCd) ? prod.prodCd.containsIgnoreCase(prodCd) : null;
    }

    private BooleanExpression createdFrom(LocalDate fromDe) {
        return fromDe != null ? invStktk.createdAt.goe(fromDe.atStartOfDay()) : null;
    }

    private BooleanExpression createdTo(LocalDate toDe) {
        // 종료일 당일을 포함하려면 다음 날 0시 미만으로 잡는다
        return toDe != null ? invStktk.createdAt.lt(toDe.plusDays(1).atStartOfDay()) : null;
    }
}
