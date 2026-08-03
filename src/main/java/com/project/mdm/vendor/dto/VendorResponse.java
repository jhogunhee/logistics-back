package com.project.mdm.vendor.dto;

import com.project.mdm.vendor.entity.Vendor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class VendorResponse {

    private final Long vendorId;
    private final String vndrCd;
    private final String vndrNm;
    private final String picNm;
    private final String telNo;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private VendorResponse(Vendor vendor) {
        this.vendorId = vendor.getId();
        this.vndrCd = vendor.getVndrCd();
        this.vndrNm = vendor.getVndrNm();
        this.picNm = vendor.getPicNm();
        this.telNo = vendor.getTelNo();
        this.createdBy = vendor.getCreatedBy();
        this.createdAt = vendor.getCreatedAt();
        this.updatedBy = vendor.getUpdatedBy();
        this.updatedAt = vendor.getUpdatedAt();
    }

    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(vendor);
    }
}
