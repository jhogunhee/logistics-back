package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.entity.OutbOrder;

import java.util.List;

public interface OutbOrderRepositoryCustom {

    List<OutbOrder> search(OutbOrderSearchCond cond);
}
