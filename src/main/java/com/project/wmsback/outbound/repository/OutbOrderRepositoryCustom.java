package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.dto.ShmtOrderResponse;
import com.project.wmsback.outbound.dto.ShmtSearchCond;
import com.project.wmsback.outbound.dto.ShmtWaveResponse;
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

    /**
     * 출고확정 화면의 웨이브 목록 — ISSUED 웨이브만. 주문 상태별 건수(확정대상 · 작업중 · 확정완료)를
     * 함께 내려 화면이 「이 웨이브에 지금 확정할 것이 있나」를 한눈에 보게 한다.
     */
    List<ShmtWaveResponse> searchShmtWaves(ShmtSearchCond cond);

    /** 출고확정 화면의 주문 목록 — 웨이브 한 건의 주문과 수량 집계(주문 · 할당 · 피킹) */
    List<ShmtOrderResponse> shmtOrders(Long wavId);
}
