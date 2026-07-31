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
    /** 발주 단위 (odr_qty의 단위) */
    private final String inbUomCd;
    /** 발주단위 1개 = 낱개(EA) 몇 개. 화면이 수량 입력 중에 환산을 재계산할 때 쓴다 */
    private final Long inbEaQty;
    /**
     * 발주 수량을 낱개(EA)로 환산한 값 — 표시용.
     * ASN 예정수량(출고단위, Prod.toOutbQty)과는 기준이 다르다 — 화면은 낱개로 통일해 보여주기로 했다
     * (출고단위는 상품마다 EA/BOX가 갈려서 합계가 어색해진다).
     */
    private final Long cnvrQty;

    private OmsIbLineResponse(OmsIbLine line) {
        this.omsIbLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tmpZon = line.getProd().getTmpZon();
        this.odrQty = line.getOdrQty();
        this.inbUomCd = line.getProd().getInbUomCd();
        this.inbEaQty = line.getProd().eaQtyOf(this.inbUomCd);
        this.cnvrQty = line.getOdrQty() * this.inbEaQty;
    }

    public static OmsIbLineResponse from(OmsIbLine line) {
        return new OmsIbLineResponse(line);
    }
}
