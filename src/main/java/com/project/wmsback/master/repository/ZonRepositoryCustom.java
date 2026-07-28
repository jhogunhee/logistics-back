package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.ZonSearchCond;
import com.project.wmsback.master.entity.Zon;

import java.util.List;

public interface ZonRepositoryCustom {

    List<Zon> search(ZonSearchCond cond);
}
