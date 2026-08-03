package com.project.wmsback.inbound.dto;

import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 적치 대상 배치 (입고 라인, Lot) 단위. 한 라인이 여러 날 나눠 검수돼 Lot이 여러 개일 수 있어
 * 라인이 아니라 (라인, Lot) 조합마다 한 행 — inv_hist를 이 조합으로 집계해 만든다.
 */
@Getter
public class PutawayCandidateResponse {

    private final Long ibLineId;
    private final Long ibOrderId;
    private final String ibNo;
    private final String vndrNm;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    private final Long lotId;
    private final String lotNo;
    private final LocalDate receiptDt;
    private final LocalDate expiryDt;
    private final Long pendingQty;

    public PutawayCandidateResponse(Long ibLineId, Long ibOrderId, String ibNo, String vndrNm,
                                     String prodCd, String prodNm, TmpZon tmpZon,
                                     Long lotId, String lotNo, LocalDate receiptDt, LocalDate expiryDt,
                                     Long pendingQty) {
        this.ibLineId = ibLineId;
        this.ibOrderId = ibOrderId;
        this.ibNo = ibNo;
        this.vndrNm = vndrNm;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.tmpZon = tmpZon;
        this.lotId = lotId;
        this.lotNo = lotNo;
        this.receiptDt = receiptDt;
        this.expiryDt = expiryDt;
        this.pendingQty = pendingQty;
    }
}
