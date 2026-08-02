package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvMovTaskResponse;
import com.project.wmsback.inventory.dto.InvMovTaskSearchCond;

import java.util.List;

public interface InvMovTaskRepositoryCustom {

    List<InvMovTaskResponse> search(InvMovTaskSearchCond cond);
}
