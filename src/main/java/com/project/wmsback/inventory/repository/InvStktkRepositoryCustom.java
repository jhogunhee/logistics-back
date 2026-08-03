package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvStktkResponse;
import com.project.wmsback.inventory.dto.InvStktkSearchCond;

import java.util.List;

public interface InvStktkRepositoryCustom {

    List<InvStktkResponse> search(InvStktkSearchCond cond);
}
