package com.project.omsback.outbound.repository;

import com.project.omsback.outbound.dto.OmsOutbOrderSearchCond;
import com.project.omsback.outbound.dto.OutbOrderRef;
import com.project.omsback.outbound.entity.OmsOutbOrder;

import java.util.Collection;
import java.util.List;

public interface OmsOutbOrderRepositoryCustom {

    List<OmsOutbOrder> search(OmsOutbOrderSearchCond cond);

    /**
     * 주문 → 생성된 WMS 출고주문 요약. 목록 한 번에 몰아 조회해 N+1을 피한다.
     * outb_order.oms_outb_order_id는 연관관계가 아니라 스칼라 컬럼이라 on 절로 직접 이어 붙인다.
     */
    List<OutbOrderRef> findOutbOrderRefs(Collection<Long> omsOutbOrderIds);
}
