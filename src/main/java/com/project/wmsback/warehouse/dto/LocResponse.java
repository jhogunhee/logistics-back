package com.project.wmsback.warehouse.dto;

import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class LocResponse {

    private final Long locId;
    private final String locCd;
    private final String zonCd;
    private final TmpZon tmpZon;
    private final LocTyp locTyp;
    private final Integer pikngPrty;
    private final Integer ptawyPrty;
    private final Long maxQty;
    /** 이 로케이션에 고정된 상품명 (fxng_loc). null = 고정 없음 — 여부 겸 표시 */
    private final String fxngProdNm;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private LocResponse(Loc loc, FxngLoc fxngLoc) {
        this.locId = loc.getId();
        this.locCd = loc.getLocCd();
        this.zonCd = loc.getZon().getZonCd();
        this.tmpZon = loc.getTmpZon();
        this.locTyp = loc.getLocTyp();
        this.pikngPrty = loc.getPikngPrty();
        this.ptawyPrty = loc.getPtawyPrty();
        this.maxQty = loc.getMaxQty();
        this.fxngProdNm = fxngLoc != null ? fxngLoc.getProd().getProdNm() : null;
        this.createdBy = loc.getCreatedBy();
        this.createdAt = loc.getCreatedAt();
        this.updatedBy = loc.getUpdatedBy();
        this.updatedAt = loc.getUpdatedAt();
    }

    public static LocResponse from(Loc loc, FxngLoc fxngLoc) {
        return new LocResponse(loc, fxngLoc);
    }

    public static LocResponse from(Loc loc) {
        return new LocResponse(loc, null);
    }
}
