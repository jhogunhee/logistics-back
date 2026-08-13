package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.entity.OutbOrder;

import java.util.List;

public interface OutbOrderRepositoryCustom {

    List<OutbOrder> search(OutbOrderSearchCond cond);

    /**
     * 검색 조건에 맞는 주문 id만 (id 오름차순). 웨이브 전략 실행이 편성 대상을 잠그며 처음 읽기
     * 위한 사전 조회다 — 엔티티로 먼저 읽으면 영속성 컨텍스트에 올라가 락을 걸어도 값이
     * 갱신되지 않는다 (「검수 동시성」과 같은 원칙).
     */
    List<Long> searchIds(OutbOrderSearchCond cond);
}
