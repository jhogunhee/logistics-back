package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbLine;

import java.util.List;

public interface OmsIbLineRepositoryCustom {

    /** 라인 + 상품을 한 번에 로딩 (라인 응답이 상품 코드/명/온도대를 쓰므로 N+1 방지) */
    List<OmsIbLine> findAllByOrderIdWithProd(Long omsIbOrderId);
}
