package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.dto.ZonSearchCond;
import com.project.wmsback.warehouse.entity.Zon;

import java.util.List;

public interface ZonRepositoryCustom {

    List<Zon> search(ZonSearchCond cond);
}
