package com.project.wmsback.warehouse.dto;

import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.entity.FxngLoc;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class FxngLocResponse {

    private final Long fxngLocId;
    private final String prodCd;
    private final String prodNm;
    private final String locCd;
    private final String zonCd;
    private final TmpZon tmpZon;
    private final Long locMaxQty;
    private final Long minQty;
    private final Long maxQty;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private FxngLocResponse(FxngLoc fxngLoc) {
        this.fxngLocId = fxngLoc.getId();
        this.prodCd = fxngLoc.getProd().getProdCd();
        this.prodNm = fxngLoc.getProd().getProdNm();
        this.locCd = fxngLoc.getLoc().getLocCd();
        this.zonCd = fxngLoc.getLoc().getZon().getZonCd();
        this.tmpZon = fxngLoc.getLoc().getTmpZon();
        this.locMaxQty = fxngLoc.getLoc().getMaxQty();
        this.minQty = fxngLoc.getMinQty();
        this.maxQty = fxngLoc.getMaxQty();
        this.createdBy = fxngLoc.getCreatedBy();
        this.createdAt = fxngLoc.getCreatedAt();
        this.updatedBy = fxngLoc.getUpdatedBy();
        this.updatedAt = fxngLoc.getUpdatedAt();
    }

    public static FxngLocResponse from(FxngLoc fxngLoc) {
        return new FxngLocResponse(fxngLoc);
    }
}
