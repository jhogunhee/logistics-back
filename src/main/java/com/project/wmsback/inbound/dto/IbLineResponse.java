package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbLine;
import com.project.mdm.prod.entity.TempZone;
import lombok.Getter;

@Getter
public class IbLineResponse {

    private final Long ibLineId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TempZone tmpZon;
    /** 유통기한(일). 검수 화면이 유통기한 기본값(검수일+일수)을 제안할 때 사용. NULL = 미관리 */
    private final Integer shelfLifeDays;
    private final Long expctQty;
    private final Long rcvdQty;
    private final Long ptawyQty;

    private IbLineResponse(IbLine line) {
        this.ibLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tmpZon = line.getProd().getTmpZon();
        this.shelfLifeDays = line.getProd().getShelfLifeDays();
        this.expctQty = line.getExpctQty();
        this.rcvdQty = line.getRcvdQty();
        this.ptawyQty = line.getPtawyQty();
    }

    public static IbLineResponse from(IbLine line) {
        return new IbLineResponse(line);
    }
}
