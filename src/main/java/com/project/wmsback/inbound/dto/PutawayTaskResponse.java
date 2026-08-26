package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 적치지시 1건 = 화면 1행. QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다
 * (잔여수량은 쿼리에서 drct-cmpl로 계산).
 */
@Getter
public class PutawayTaskResponse {

    private final Long putawayTaskId;
    private final Long ibLineId;
    private final Long ibOrderId;
    private final String ibNo;
    /** 상대처 — 정상 발주는 벤더, 반품입고는 점포. 둘 중 하나만 채워진다 (ck_ib_order_vndr_store) */
    private final String vndrNm;
    private final String storeNm;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    private final Long lotId;
    private final String lotNo;
    private final LocalDate receiptDt;
    private final LocalDate expiryDt;
    private final Long toLocId;
    private final String toLocCd;
    private final Long drctQty;
    private final Long cmplQty;
    /** 잔여수량 = 지시 - 완료 (파생값). 쿼리에서 계산해 내려준다 */
    private final Long remainingQty;
    private final PutawayTaskStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime cmplDt;

    public PutawayTaskResponse(Long putawayTaskId, Long ibLineId, Long ibOrderId, String ibNo,
                               String vndrNm, String storeNm,
                               String prodCd, String prodNm, TmpZon tmpZon,
                               Long lotId, String lotNo, LocalDate receiptDt, LocalDate expiryDt,
                               Long toLocId, String toLocCd,
                               Long drctQty, Long cmplQty, Long remainingQty,
                               PutawayTaskStatus status, LocalDateTime createdAt, LocalDateTime cmplDt) {
        this.putawayTaskId = putawayTaskId;
        this.ibLineId = ibLineId;
        this.ibOrderId = ibOrderId;
        this.ibNo = ibNo;
        this.vndrNm = vndrNm;
        this.storeNm = storeNm;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.tmpZon = tmpZon;
        this.lotId = lotId;
        this.lotNo = lotNo;
        this.receiptDt = receiptDt;
        this.expiryDt = expiryDt;
        this.toLocId = toLocId;
        this.toLocCd = toLocCd;
        this.drctQty = drctQty;
        this.cmplQty = cmplQty;
        this.remainingQty = remainingQty;
        this.status = status;
        this.createdAt = createdAt;
        this.cmplDt = cmplDt;
    }
}
