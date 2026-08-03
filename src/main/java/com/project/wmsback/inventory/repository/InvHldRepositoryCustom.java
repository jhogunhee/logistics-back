package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvHldResponse;
import com.project.wmsback.inventory.dto.InvHldSearchCond;

import java.util.List;

public interface InvHldRepositoryCustom {

    List<InvHldResponse> search(InvHldSearchCond cond);
}
