package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.LocSearchCond;
import com.project.wmsback.master.entity.Loc;

import java.util.List;

public interface LocRepositoryCustom {

    List<Loc> search(LocSearchCond cond);
}
