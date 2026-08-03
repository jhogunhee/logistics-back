package com.project.wmsback.outbound.dto;

import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.outbound.entity.OutbLine;
import lombok.Getter;

@Getter
public class OutbLineResponse {

    private final Long outbLineId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    private final Long odrQty;

    private OutbLineResponse(OutbLine line) {
        this.outbLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tmpZon = line.getProd().getTmpZon();
        this.odrQty = line.getOdrQty();
    }

    public static OutbLineResponse from(OutbLine line) {
        return new OutbLineResponse(line);
    }
}
