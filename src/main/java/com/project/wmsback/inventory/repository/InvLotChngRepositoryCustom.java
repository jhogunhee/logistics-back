package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvLotChngResponse;
import com.project.wmsback.inventory.dto.InvLotChngSearchCond;

import java.util.List;

public interface InvLotChngRepositoryCustom {

    /** 로트변경 실적 조회 (append-only 로그) */
    List<InvLotChngResponse> search(InvLotChngSearchCond cond);
}
