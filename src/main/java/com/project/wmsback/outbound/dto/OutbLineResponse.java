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
    /**
     * 할당 수량. outb_alloc 집계라 라인이 갖고 있지 않다 — 밖에서 받는다
     * ({@link OutbOrderResponse#getTotalAlocQty()}와 같은 이유).
     */
    private final Long alocQty;

    private OutbLineResponse(OutbLine line, Long alocQty) {
        this.outbLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tmpZon = line.getProd().getTmpZon();
        this.odrQty = line.getOdrQty();
        this.alocQty = alocQty;
    }

    public static OutbLineResponse from(OutbLine line, Long alocQty) {
        return new OutbLineResponse(line, alocQty);
    }
}
