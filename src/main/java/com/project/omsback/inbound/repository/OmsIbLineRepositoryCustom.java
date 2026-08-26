package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbLine;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface OmsIbLineRepositoryCustom {

    /** 라인 + 상품을 한 번에 로딩 (라인 응답이 상품 코드/명/온도대를 쓰므로 N+1 방지) */
    List<OmsIbLine> findAllByOrderIdWithProd(Long omsIbOrderId);

    /**
     * 아직 확정되지 않은(CREATED) 발주의 상품별 수량 합. 자동발주 산정이 「이미 시켜둔 것」으로 센다.
     * 단위는 <b>입고단위</b>다 — {@code odr_qty}가 그렇다. 낱개 환산은 부르는 쪽 몫이다.
     * 반품 발주는 뺀다(단위가 출고단위라 같은 자리에 더할 수 없고, 벤더에게 시킨 물건도 아니다).
     */
    Map<Long, Long> openOdrQtyByProd(Collection<Long> prodIds);
}
