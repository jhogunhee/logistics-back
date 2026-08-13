package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbOrder;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IbOrderRepositoryCustom {

    List<IbOrder> search(IbOrderSearchCond cond);

    /**
     * 입고라인별 최종 검수일시 — inv_hist의 RECEIVE 행 중 가장 늦은 created_at.
     * <p>
     * 최초가 아니라 최종인 이유: 최초는 한 번 찍히면 갱신되지 않아 「착수했다」만 말한다.
     * 라인이 여럿이면 첫 라인 하나만 반영되고 나머지가 나중에 검수돼도 값이 그대로다.
     * 최종은 계속 갱신되므로 「마지막으로 움직인 때」를 말하고 전량검수 완료 시점과도 맞는다.
     * <p>
     * ib_order에 컬럼을 두지 않고 원장에서 파생하는 이유: 검수는 이미 inv_hist에 RECEIVE 행을
     * 남기며 ib_line_id를 채우므로 원천이 있고, 캐시 컬럼을 늘리면 갱신 누락 지점이 하나 는다.
     *
     * @return 입고라인 id → 최종 검수일시 (검수 이력이 없는 라인은 결과에서 빠진다)
     */
    Map<Long, LocalDateTime> lastReceiveDtByLine(Collection<Long> ibLineIds);
}
