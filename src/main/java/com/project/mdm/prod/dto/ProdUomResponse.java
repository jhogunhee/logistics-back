package com.project.mdm.prod.dto;

import com.project.mdm.prod.entity.ProdUom;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 포장 응답 행 — 단위 관리 화면의 그리드 한 줄. 상품 3필드(prodId·prodCd·prodNm)를
 * 함께 실어 이 행만으로 어느 상품의 포장인지 알 수 있다 (화면이 prodId로 묶어 쓴다).
 */
@Getter
public class ProdUomResponse {

    private final Long prodUomId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final String uomCd;
    private final Long eaQty;
    private final BigDecimal wgt;
    /** 이 포장이 상품의 입고단위인가. 화면이 표시하고, 삭제 가드의 근거이기도 하다 */
    private final boolean inbUom;
    /** 이 포장이 상품의 출고단위(=재고 저장 단위)인가 */
    private final boolean outbUom;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private ProdUomResponse(ProdUom uom) {
        this.prodUomId = uom.getId();
        this.prodId = uom.getProd().getId();
        this.prodCd = uom.getProd().getProdCd();
        this.prodNm = uom.getProd().getProdNm();
        this.uomCd = uom.getUomCd();
        this.eaQty = uom.getEaQty();
        this.wgt = uom.getWgt();
        this.inbUom = uom.getUomCd().equals(uom.getProd().getInbUomCd());
        this.outbUom = uom.getUomCd().equals(uom.getProd().getOutbUomCd());
        this.createdBy = uom.getCreatedBy();
        this.createdAt = uom.getCreatedAt();
        this.updatedBy = uom.getUpdatedBy();
        this.updatedAt = uom.getUpdatedAt();
    }

    public static ProdUomResponse from(ProdUom uom) {
        return new ProdUomResponse(uom);
    }
}
