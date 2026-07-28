package com.project.wmsback.outbound.dto;

import com.project.wmsback.master.entity.TempZone;
import com.project.wmsback.outbound.entity.OutbLine;
import lombok.Getter;

@Getter
public class OutbLineResponse {

    private final Long outbLineId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TempZone tempZone;
    private final Long orderQty;

    private OutbLineResponse(OutbLine line) {
        this.outbLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tempZone = line.getProd().getTempZone();
        this.orderQty = line.getOrderQty();
    }

    public static OutbLineResponse from(OutbLine line) {
        return new OutbLineResponse(line);
    }
}
