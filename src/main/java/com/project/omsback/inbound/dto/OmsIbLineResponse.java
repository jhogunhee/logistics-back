package com.project.omsback.inbound.dto;

import com.project.omsback.inbound.entity.OmsIbLine;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

@Getter
public class OmsIbLineResponse {

    private final Long omsIbLineId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    /** 유통기한(일). null = 미관리 — 화면이 이 값으로 「미관리」를 그리므로 빠지면 전부 미관리로 보인다 */
    private final Integer shelfLifeDays;
    private final Long odrQty;
    /** 발주 단위 (odr_qty의 단위) */
    private final String inbUomCd;
    /** 발주단위 1개 = 낱개(EA) 몇 개. 화면이 수량 입력 중에 환산을 재계산할 때 쓴다 */
    private final Long inbEaQty;
    /**
     * 발주 수량을 낱개(EA)로 환산한 값 — 표시용.
     * ASN 예정수량도 같은 낱개(EA) 기준이다 (재고 저장 단위가 EA로 통일되면서 둘이 일치하게 됐다).
     */
    private final Long cnvrQty;

    private OmsIbLineResponse(OmsIbLine line) {
        this.omsIbLineId = line.getId();
        this.prodId = line.getProd().getId();
        this.prodCd = line.getProd().getProdCd();
        this.prodNm = line.getProd().getProdNm();
        this.tmpZon = line.getProd().getTmpZon();
        this.shelfLifeDays = line.getProd().getShelfLifeDays();
        this.odrQty = line.getOdrQty();
        this.inbUomCd = line.getProd().getInbUomCd();
        this.inbEaQty = line.getProd().eaQtyOf(this.inbUomCd);
        this.cnvrQty = line.getOdrQty() * this.inbEaQty;
    }

    public static OmsIbLineResponse from(OmsIbLine line) {
        return new OmsIbLineResponse(line);
    }
}
