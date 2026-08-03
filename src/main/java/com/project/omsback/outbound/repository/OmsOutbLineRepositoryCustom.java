package com.project.omsback.outbound.repository;

import com.project.omsback.outbound.entity.OmsOutbLine;

import java.util.List;

public interface OmsOutbLineRepositoryCustom {

    /** 라인 + 상품을 한 번에 로딩 (라인 응답이 상품 코드/명/온도대/출고단위를 쓰므로 N+1 방지) */
    List<OmsOutbLine> findAllByOrderIdWithProd(Long omsOutbOrderId);
}
