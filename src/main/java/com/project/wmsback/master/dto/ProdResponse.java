package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ProdResponse {

    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TempZone tempZone;
    private final Integer shelfLifeDays;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private ProdResponse(Prod prod) {
        this.prodId = prod.getId();
        this.prodCd = prod.getProdCd();
        this.prodNm = prod.getProdNm();
        this.tempZone = prod.getTempZone();
        this.shelfLifeDays = prod.getShelfLifeDays();
        this.createdBy = prod.getCreatedBy();
        this.createdAt = prod.getCreatedAt();
        this.updatedBy = prod.getUpdatedBy();
        this.updatedAt = prod.getUpdatedAt();
    }

    public static ProdResponse from(Prod prod) {
        return new ProdResponse(prod);
    }
}