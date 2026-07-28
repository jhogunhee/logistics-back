package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.dto.AsnRef;
import com.project.omsback.inbound.dto.OmsIbOrderSearchCond;
import com.project.omsback.inbound.entity.OmsIbOrder;

import java.util.Collection;
import java.util.List;

public interface OmsIbOrderRepositoryCustom {

    List<OmsIbOrder> search(OmsIbOrderSearchCond cond);

    /**
     * 주문 → 생성된 ASN 요약. 목록 한 번에 몰아 조회해 N+1을 피한다.
     * ib_order.oms_ib_order_id는 연관관계가 아니라 스칼라 컬럼이라 on 절로 직접 이어 붙인다.
     */
    List<AsnRef> findAsnRefs(Collection<Long> omsIbOrderIds);
}
