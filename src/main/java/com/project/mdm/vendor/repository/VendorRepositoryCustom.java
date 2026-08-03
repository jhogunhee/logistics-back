package com.project.mdm.vendor.repository;

import com.project.mdm.vendor.dto.VendorSearchCond;
import com.project.mdm.vendor.entity.Vendor;

import java.util.List;

public interface VendorRepositoryCustom {

    List<Vendor> search(VendorSearchCond cond);
}
