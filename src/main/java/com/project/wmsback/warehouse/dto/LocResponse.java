package com.project.wmsback.warehouse.dto;

import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocType;
import com.project.mdm.prod.entity.TempZone;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class LocResponse {

    private final Long locId;
    private final String locCd;
    private final String zonCd;
    private final TempZone tmpZon;
    private final LocType locTyp;
    private final Integer pikngPrty;
    private final Integer ptawyPrty;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private LocResponse(Loc loc) {
        this.locId = loc.getId();
        this.locCd = loc.getLocCd();
        this.zonCd = loc.getZonCd();
        this.tmpZon = loc.getTmpZon();
        this.locTyp = loc.getLocTyp();
        this.pikngPrty = loc.getPikngPrty();
        this.ptawyPrty = loc.getPtawyPrty();
        this.createdBy = loc.getCreatedBy();
        this.createdAt = loc.getCreatedAt();
        this.updatedBy = loc.getUpdatedBy();
        this.updatedAt = loc.getUpdatedAt();
    }

    public static LocResponse from(Loc loc) {
        return new LocResponse(loc);
    }
}
