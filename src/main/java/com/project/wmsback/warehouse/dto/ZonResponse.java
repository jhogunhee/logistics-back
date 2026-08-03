package com.project.wmsback.warehouse.dto;

import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.StrgTyp;
import com.project.mdm.prod.entity.TempZone;
import com.project.wmsback.warehouse.entity.Zon;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ZonResponse {

    private final Long zonId;
    private final String zonCd;
    private final String zonNm;
    private final TempZone tmpZon;
    private final StrgTyp strgTyp;
    private final BizDvsn bizDvsn;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private ZonResponse(Zon zon) {
        this.zonId = zon.getId();
        this.zonCd = zon.getZonCd();
        this.zonNm = zon.getZonNm();
        this.tmpZon = zon.getTmpZon();
        this.strgTyp = zon.getStrgTyp();
        this.bizDvsn = zon.getBizDvsn();
        this.createdBy = zon.getCreatedBy();
        this.createdAt = zon.getCreatedAt();
        this.updatedBy = zon.getUpdatedBy();
        this.updatedAt = zon.getUpdatedAt();
    }

    public static ZonResponse from(Zon zon) {
        return new ZonResponse(zon);
    }
}
