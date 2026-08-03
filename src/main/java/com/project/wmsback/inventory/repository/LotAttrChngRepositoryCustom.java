package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.LotAttrChngResponse;
import com.project.wmsback.inventory.dto.LotAttrChngSearchCond;

import java.util.List;

public interface LotAttrChngRepositoryCustom {

    List<LotAttrChngResponse> search(LotAttrChngSearchCond cond);
}
