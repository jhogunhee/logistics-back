package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.OutbWaveSearchCond;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WaveStatus;
import com.querydsl.core.types.dsl.BooleanExpression;
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
                        waveNoContains(cond.getWaveNo()),
                        statusEq(cond.getStatus())
                )
                .orderBy(outbWave.id.desc())
                .fetch();
    }

    private BooleanExpression waveNoContains(String waveNo) {
        return StringUtils.hasText(waveNo) ? outbWave.waveNo.containsIgnoreCase(waveNo) : null;
    }

    private BooleanExpression statusEq(WaveStatus status) {
        return status != null ? outbWave.status.eq(status) : null;
    }
}
