package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvStktkLnResponse;

import java.util.List;

public interface InvStktkLnRepositoryCustom {

    /** 조사 라인 조회. 현재 재고(전산수량·예약·보류)를 재고 키로 left join해 함께 내려준다 */
    List<InvStktkLnResponse> searchByStktkId(Long stktkId);
}
