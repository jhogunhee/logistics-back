package com.project.mdm.prod.dto;

import com.project.mdm.prod.entity.ProdVndr;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ProdVndrResponse {

    private final Long prodVndrId;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    /** 발주 수량의 단위 — 화면이 최소주문수량 옆에 붙여 보여준다 */
    private final String inbUomCd;
    private final String vndrCd;
    private final String vndrNm;
    private final Long minQty;
    private final Long maxQty;
    private final Long minOdrQty;
    private final Integer leadDays;
    private final Integer prty;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private ProdVndrResponse(ProdVndr prodVndr) {
        this.prodVndrId = prodVndr.getId();
        this.prodCd = prodVndr.getProd().getProdCd();
        this.prodNm = prodVndr.getProd().getProdNm();
        this.tmpZon = prodVndr.getProd().getTmpZon();
        this.inbUomCd = prodVndr.getProd().getInbUomCd();
        this.vndrCd = prodVndr.getVendor().getVndrCd();
        this.vndrNm = prodVndr.getVendor().getVndrNm();
        this.minQty = prodVndr.getMinQty();
        this.maxQty = prodVndr.getMaxQty();
        this.minOdrQty = prodVndr.getMinOdrQty();
        this.leadDays = prodVndr.getLeadDays();
        this.prty = prodVndr.getPrty();
        this.createdBy = prodVndr.getCreatedBy();
        this.createdAt = prodVndr.getCreatedAt();
        this.updatedBy = prodVndr.getUpdatedBy();
        this.updatedAt = prodVndr.getUpdatedAt();
    }

    public static ProdVndrResponse from(ProdVndr prodVndr) {
        return new ProdVndrResponse(prodVndr);
    }
}
