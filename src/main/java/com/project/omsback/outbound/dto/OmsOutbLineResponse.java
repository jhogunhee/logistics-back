package com.project.omsback.outbound.dto;

import com.project.mdm.prod.entity.TmpZon;
import com.project.omsback.outbound.entity.OmsOutbLine;
import lombok.Getter;

@Getter
public class OmsOutbLineResponse {

    private final Long omsOutbLineId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    /** 유통기한(일). null = 미관리 — 화면이 이 값으로 「미관리」를 그리므로 빠지면 전부 미관리로 보인다 */
    private final Integer shelfLifeDays;
    private final Long odrQty;
    /** 주문 단위 (odr_qty의 단위). 출고단위라 확정 후에도 그대로다 — 환산 컬럼이 없는 이유 */
    private final String outbUomCd;

    private OmsOutbLineResponse(OmsOutbLine line) {
        this.omsOutbLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tmpZon = line.getProd().getTmpZon();
        this.shelfLifeDays = line.getProd().getShelfLifeDays();
        this.odrQty = line.getOdrQty();
        this.outbUomCd = line.getProd().getOutbUomCd();
    }

    public static OmsOutbLineResponse from(OmsOutbLine line) {
        return new OmsOutbLineResponse(line);
    }
}
