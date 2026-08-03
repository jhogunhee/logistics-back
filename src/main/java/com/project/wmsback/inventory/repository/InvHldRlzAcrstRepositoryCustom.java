package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvHldAcrstResponse;
import com.project.wmsback.inventory.dto.InvHldAcrstSearchCond;

import java.util.List;

public interface InvHldRlzAcrstRepositoryCustom {

    List<InvHldAcrstResponse> search(InvHldAcrstSearchCond cond);
}
