package com.project.wmsback.warehouse.dto;

import com.project.wmsback.warehouse.entity.Lot;
import lombok.Getter;

import java.time.LocalDate;

/** Lot 조회 응답 (읽기 전용 — Lot 생성은 검수의 소관이다) */
@Getter
public class LotResponse {

    private final Long lotId;
    private final Long prodId;
    private final String prodCd;
    private final String lotNo;
    private final LocalDate receiptDt;
    private final LocalDate mfgDt;
    private final LocalDate expiryDt;

    private LotResponse(Lot lot) {
        this.lotId = lot.getId();
        this.prodId = lot.getProd().getId();
        this.prodCd = lot.getProd().getProdCd();
        this.lotNo = lot.getLotNo();
        this.receiptDt = lot.getReceiptDt();
        this.mfgDt = lot.getMfgDt();
        this.expiryDt = lot.getExpiryDt();
    }

    public static LotResponse from(Lot lot) {
        return new LotResponse(lot);
    }
}
