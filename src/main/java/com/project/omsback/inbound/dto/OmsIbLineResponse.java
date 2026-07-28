package com.project.omsback.inbound.dto;

import com.project.omsback.inbound.entity.OmsIbLine;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;

@Getter
public class OmsIbLineResponse {

    private final Long omsIbLineId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TempZone tmpZon;
    private final Long odrQty;

    private OmsIbLineResponse(OmsIbLine line) {
        this.omsIbLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tmpZon = line.getProd().getTmpZon();
        this.odrQty = line.getOdrQty();
    }

    public static OmsIbLineResponse from(OmsIbLine line) {
        return new OmsIbLineResponse(line);
    }
}
