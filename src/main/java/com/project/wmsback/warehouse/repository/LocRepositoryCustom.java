package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.dto.LocSearchCond;
import com.project.wmsback.warehouse.entity.Loc;

import java.util.List;

public interface LocRepositoryCustom {

    List<Loc> search(LocSearchCond cond);
}
