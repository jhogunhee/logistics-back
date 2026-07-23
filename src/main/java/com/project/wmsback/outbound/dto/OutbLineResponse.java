package com.project.wmsback.outbound.dto;

import com.project.wmsback.master.entity.TempZone;
import com.project.wmsback.outbound.entity.OutbLine;
import lombok.Getter;

@Getter
public class OutbLineResponse {

    private final Long outbLineId;
    private final Long skuId;
    private final String skuCd;
    private final String skuNm;
    private final TempZone tempZone;
    private final Long orderQty;

    private OutbLineResponse(OutbLine line) {
        this.outbLineId = line.getId();
        this.skuId = line.getSku().getId();
        this.skuCd = line.getSku().getSkuCd();
        this.skuNm = line.getSku().getSkuNm();
        this.tempZone = line.getSku().getTempZone();
        this.orderQty = line.getOrderQty();
    }

    public static OutbLineResponse from(OutbLine line) {
        return new OutbLineResponse(line);
    }
}
