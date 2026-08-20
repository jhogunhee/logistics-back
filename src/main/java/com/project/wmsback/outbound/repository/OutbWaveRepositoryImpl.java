package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.OutbWaveSearchCond;
import com.project.wmsback.outbound.entity.QOutbOrder;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.project.wmsback.outbound.entity.QOutbWave.outbWave;

@RequiredArgsConstructor
public class OutbWaveRepositoryImpl implements OutbWaveRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<OutbWave> search(OutbWaveSearchCond cond) {
        // orderCount 집계용 orders(단일 컬렉션)를 fetch join으로 함께 로딩
        return queryFactory
                .selectFrom(outbWave).distinct()
                .leftJoin(outbWave.orders).fetchJoin()
                .where(
                        waveNoContains(cond.getWavNo()),
                        statusEq(cond.getStatus()),
                        withinExpctDe(cond)
                )
                .orderBy(outbWave.id.desc())
                .fetch();
    }

    private BooleanExpression waveNoContains(String wavNo) {
        return StringUtils.hasText(wavNo) ? outbWave.wavNo.containsIgnoreCase(wavNo) : null;
    }

    private BooleanExpression statusEq(WaveStatus status) {
        return status != null ? outbWave.status.eq(status) : null;
    }

    /**
     * 출고예정일 — 웨이브에 없는 값이라 <b>소속 주문을 EXISTS로 본다</b>
     * (편성 가드가 웨이브의 출고예정일을 하나로 강제하므로 어느 주문을 봐도 같다).
     *
     * <p><b>주문이 하나도 없는 웨이브는 기간과 무관하게 남긴다.</b> 빈 웨이브는 날짜를 가질 수
     * 없어 EXISTS만 걸면 방금 만든 웨이브가 목록에서 곧바로 사라진다 — 담을 대상이 바로 그
     * 웨이브인데 보이지 않게 된다. 담기 화면도 빈 웨이브(날짜 NULL)를 첫 담기가 날짜를 정하는
     * 상태로 다루므로 여기서도 같은 취급을 한다.
     */
    private BooleanExpression withinExpctDe(OutbWaveSearchCond cond) {
        boolean hasFrom = cond.getExpctDeFrom() != null;
        boolean hasTo = cond.getExpctDeTo() != null;
        if (!hasFrom && !hasTo) {
            return null;
        }
        QOutbOrder matchOrder = new QOutbOrder("deMatchOrder");
        BooleanExpression matching = JPAExpressions.selectOne()
                .from(matchOrder)
                .where(
                        matchOrder.wave.id.eq(outbWave.id),
                        hasFrom ? matchOrder.expctDe.goe(cond.getExpctDeFrom()) : null,
                        hasTo ? matchOrder.expctDe.loe(cond.getExpctDeTo()) : null
                )
                .exists();

        QOutbOrder anyOrder = new QOutbOrder("deAnyOrder");
        BooleanExpression empty = JPAExpressions.selectOne()
                .from(anyOrder)
                .where(anyOrder.wave.id.eq(outbWave.id))
                .notExists();

        return matching.or(empty);
    }
}
