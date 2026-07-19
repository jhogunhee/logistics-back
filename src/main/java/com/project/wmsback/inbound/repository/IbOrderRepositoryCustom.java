package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbOrder;

import java.util.List;

public interface IbOrderRepositoryCustom {

    List<IbOrder> search(IbOrderSearchCond cond);
}
