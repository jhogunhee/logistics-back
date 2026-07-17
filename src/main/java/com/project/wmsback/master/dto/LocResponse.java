package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.LocType;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class LocResponse {

    private final Long locId;
    private final String locCd;
    private final String zoneCd;
    private final TempZone tempZone;
    private final LocType locType;
    private final Integer pickPrty;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private LocResponse(Loc loc) {
        this.locId = loc.getId();
        this.locCd = loc.getLocCd();
        this.zoneCd = loc.getZoneCd();
        this.tempZone = loc.getTempZone();
        this.locType = loc.getLocType();
        this.pickPrty = loc.getPickPrty();
        this.createdBy = loc.getCreatedBy();
        this.createdAt = loc.getCreatedAt();
        this.updatedBy = loc.getUpdatedBy();
        this.updatedAt = loc.getUpdatedAt();
    }

    public static LocResponse from(Loc loc) {
        return new LocResponse(loc);
    }
}
