package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;

@Getter
public class IbLineResponse {

    private final Long ibLineId;
    private final Long skuId;
    private final String skuCd;
    private final String skuNm;
    private final TempZone tempZone;
    /** 유통기한(일). 검수 화면이 유통기한 기본값(검수일+일수)을 제안할 때 사용. NULL = 미관리 */
    private final Integer shelfLifeDays;
    private final Long expctQty;
    private final Long rcvdQty;
    private final Long ptwyQty;

    private IbLineResponse(IbLine line) {
        this.ibLineId = line.getId();
        this.skuId = line.getSku().getId();
        this.skuCd = line.getSku().getSkuCd();
        this.skuNm = line.getSku().getSkuNm();
        this.tempZone = line.getSku().getTempZone();
        this.shelfLifeDays = line.getSku().getShelfLifeDays();
        this.expctQty = line.getExpctQty();
        this.rcvdQty = line.getRcvdQty();
        this.ptwyQty = line.getPtwyQty();
    }

    public static IbLineResponse from(IbLine line) {
        return new IbLineResponse(line);
    }
}
