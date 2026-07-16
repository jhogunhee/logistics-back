package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.SkuSearchCond;
import com.project.wmsback.master.entity.Sku;

import java.util.List;

public interface SkuRepositoryCustom {

    List<Sku> search(SkuSearchCond cond);
}