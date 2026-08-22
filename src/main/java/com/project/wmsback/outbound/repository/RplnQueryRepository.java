package com.project.wmsback.outbound.repository;

import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.entity.QInvMovTask;
import com.project.wmsback.outbound.dto.RplnRowResponse;
import com.project.wmsback.outbound.dto.RplnSearchCond;
import com.project.wmsback.outbound.dto.RplnWaveResponse;
import com.project.wmsback.outbound.entity.QPikngTask;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.project.wmsback.inventory.entity.QInvMovTask.invMovTask;
import static com.project.wmsback.outbound.entity.QOutbAlloc.outbAlloc;
import static com.project.wmsback.outbound.entity.QOutbLine.outbLine;
import static com.project.wmsback.outbound.entity.QOutbOrder.outbOrder;
import static com.project.wmsback.outbound.entity.QOutbWave.outbWave;
import static com.project.wmsback.outbound.entity.QPikngTask.pikngTask;

/**
 * 보충 화면 조회 포트 — 보충지시(RPLN)를 짝 피킹지시 → 할당 → 주문 → 웨이브로 이어 읽는다.
 * 보충지시는 피킹지시를 {@code pikng_task_id} 스칼라로만 가리키므로(FK 없음) 조인은 id 비교다.
 * Spring Data 인터페이스 없이 {@code JPAQueryFactory}만 드는 읽기 전용 포트(LocCapacityQueryRepository와 같은 형태).
 */
@Repository
@RequiredArgsConstructor
public class RplnQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** 보충지시가 있는 ISSUED 웨이브. 미확정 건수는 0이 아니면 화면이 강조한다 */
    public List<RplnWaveResponse> searchWaves(RplnSearchCond cond) {
        NumberExpression<Long> openCount = new CaseBuilder()
                .when(invMovTask.status.eq(InvMovStatus.DIRECTED)).then(1L).otherwise(0L).sum();
        List<Tuple> rows = queryFactory
                .select(outbWave.id, outbWave.wavNo, outbWave.issuedDt, outbOrder.expctDe.min(),
                        invMovTask.count(), openCount)
                .from(invMovTask)
                .join(pikngTask).on(pikngTask.id.eq(invMovTask.pikngTaskId))
                .join(pikngTask.wave, outbWave)
                .join(pikngTask.outbAlloc, outbAlloc)
                .join(outbAlloc.outbLine, outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .where(
                        invMovTask.movDvsn.eq(InvMovDvsn.RPLN),
                        invMovTask.status.ne(InvMovStatus.CANCELLED),
                        outbWave.status.eq(WaveStatus.ISSUED),
                        StringUtils.hasText(cond.getWavNo()) ? outbWave.wavNo.containsIgnoreCase(cond.getWavNo()) : null,
                        cond.getExpctDeFrom() != null ? outbOrder.expctDe.goe(cond.getExpctDeFrom()) : null,
                        cond.getExpctDeTo() != null ? outbOrder.expctDe.loe(cond.getExpctDeTo()) : null,
                        matchingProdExists(cond.getProdCd())
                )
                .groupBy(outbWave.id, outbWave.wavNo, outbWave.issuedDt)
                .orderBy(outbWave.id.desc())
                .fetch();

        List<RplnWaveResponse> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            Long open = row.get(openCount);
            result.add(new RplnWaveResponse(row.get(outbWave.id), row.get(outbWave.wavNo),
                    row.get(outbOrder.expctDe.min()), row.get(outbWave.issuedDt),
                    row.get(invMovTask.count()), open != null ? open : 0L));
        }
        return result;
    }

    /** 웨이브의 보충지시 — 피킹 순번 순 (취소된 것 제외) */
    public List<RplnRowResponse> rows(Long wavId) {
        List<Tuple> rows = queryFactory
                .select(invMovTask.id, invMovTask.invMovNo, pikngTask.id, pikngTask.srtSeq,
                        outbOrder.outbNo, outbOrder.store.storeNm,
                        invMovTask.prod.prodCd, invMovTask.prod.prodNm,
                        invMovTask.lot.lotNo, invMovTask.lot.expiryDt,
                        invMovTask.fromLoc.locCd, invMovTask.toLoc.locCd,
                        invMovTask.drctQty, invMovTask.status, invMovTask.cmplDt)
                .from(invMovTask)
                .join(pikngTask).on(pikngTask.id.eq(invMovTask.pikngTaskId))
                .join(pikngTask.outbAlloc, outbAlloc)
                .join(outbAlloc.outbLine, outbLine)
                .join(outbLine.outbOrder, outbOrder)
                .where(
                        invMovTask.movDvsn.eq(InvMovDvsn.RPLN),
                        invMovTask.status.ne(InvMovStatus.CANCELLED),
                        pikngTask.wave.id.eq(wavId)
                )
                .orderBy(pikngTask.srtSeq.asc())
                .fetch();

        List<RplnRowResponse> result = new ArrayList<>(rows.size());
        for (Tuple row : rows) {
            Long qty = row.get(invMovTask.drctQty);
            result.add(new RplnRowResponse(row.get(invMovTask.id), row.get(invMovTask.invMovNo),
                    row.get(pikngTask.id), row.get(pikngTask.srtSeq),
                    row.get(outbOrder.outbNo), row.get(outbOrder.store.storeNm),
                    row.get(invMovTask.prod.prodCd), row.get(invMovTask.prod.prodNm),
                    row.get(invMovTask.lot.lotNo), row.get(invMovTask.lot.expiryDt),
                    row.get(invMovTask.fromLoc.locCd), row.get(invMovTask.toLoc.locCd),
                    qty != null ? qty : 0L, row.get(invMovTask.status), row.get(invMovTask.cmplDt)));
        }
        return result;
    }

    private BooleanExpression matchingProdExists(String prodCd) {
        if (!StringUtils.hasText(prodCd)) {
            return null;
        }
        QInvMovTask other = new QInvMovTask("matchRpln");
        QPikngTask otherTask = new QPikngTask("matchTask");
        return JPAExpressions.selectOne()
                .from(other)
                .join(otherTask).on(otherTask.id.eq(other.pikngTaskId))
                .where(otherTask.wave.eq(outbWave),
                        other.movDvsn.eq(InvMovDvsn.RPLN),
                        other.status.ne(InvMovStatus.CANCELLED),
                        other.prod.prodCd.containsIgnoreCase(prodCd))
                .exists();
    }
}
