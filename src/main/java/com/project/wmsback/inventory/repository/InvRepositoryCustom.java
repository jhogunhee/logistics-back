package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvResponse;
import com.project.wmsback.inventory.dto.InvSearchCond;

import java.util.List;

public interface InvRepositoryCustom {

    /** 현재고 조회 화면용 검색. SKU+Loc+Lot 조인 결과에 가용수량(보유-할당)을 계산해 함께 내려준다 */
    List<InvResponse> search(InvSearchCond cond);
}
