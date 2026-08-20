package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.dto.FxngLocSearchCond;
import com.project.wmsback.warehouse.entity.FxngLoc;

import java.util.List;

public interface FxngLocRepositoryCustom {

    List<FxngLoc> search(FxngLocSearchCond cond);
}
