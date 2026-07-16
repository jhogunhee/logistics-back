package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.Sku;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;

@Getter
public class SkuResponse {

    private final Long skuId;
    private final String skuCd;
    private final String skuNm;
    private final TempZone tempZone;
    private final Integer shelfLifeDays;

    private SkuResponse(Sku sku) {
        this.skuId = sku.getId();
        this.skuCd = sku.getSkuCd();
        this.skuNm = sku.getSkuNm();
        this.tempZone = sku.getTempZone();
        this.shelfLifeDays = sku.getShelfLifeDays();
    }

    public static SkuResponse from(Sku sku) {
        return new SkuResponse(sku);
    }
}