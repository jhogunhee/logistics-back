package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.VendorSearchCond;
import com.project.wmsback.master.entity.Vendor;

import java.util.List;

public interface VendorRepositoryCustom {

    List<Vendor> search(VendorSearchCond cond);
}
