package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ProdResponse {

    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TempZone tmpZon;
    private final String inbUomCd;
    private final String outbUomCd;
    private final Integer shelfLifeDays;
    private final List<ProdUomResponse> uoms;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private ProdResponse(Prod prod) {
        this.prodId = prod.getId();
        this.prodCd = prod.getProdCd();
        this.prodNm = prod.getProdNm();
        this.tmpZon = prod.getTmpZon();
        this.inbUomCd = prod.getInbUomCd();
        this.outbUomCd = prod.getOutbUomCd();
        this.shelfLifeDays = prod.getShelfLifeDays();
        this.uoms = prod.getUoms().stream().map(ProdUomResponse::from).toList();
        this.createdBy = prod.getCreatedBy();
        this.createdAt = prod.getCreatedAt();
        this.updatedBy = prod.getUpdatedBy();
        this.updatedAt = prod.getUpdatedAt();
    }

    public static ProdResponse from(Prod prod) {
        return new ProdResponse(prod);
    }
}
