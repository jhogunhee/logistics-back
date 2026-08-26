package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvAdjResponse;
import com.project.wmsback.inventory.dto.InvAdjSearchCond;

import java.util.List;

public interface InvAdjRepositoryCustom {

    /** 재고조정 실적 조회 (append-only 로그) */
    List<InvAdjResponse> search(InvAdjSearchCond cond);
}
