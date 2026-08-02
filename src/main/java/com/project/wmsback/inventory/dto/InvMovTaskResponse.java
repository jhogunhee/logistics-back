package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 이동지시 1건 = 화면 1행. QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다
 * (잔여수량은 쿼리에서 drct-cmpl로 계산).
 */
@Getter
public class InvMovTaskResponse {

    private final Long invMovTaskId;
    private final String invMovNo;
    private final InvMovDvsn movDvsn;
    private final String prodCd;
    private final String prodNm;
    private final String lotNo;
    private final LocalDate expiryDt;
    private final String fromLocCd;
    private final String toLocCd;
    private final Long drctQty;
    private final Long cmplQty;
    /** 잔여수량 = 지시 - 완료 (파생값). 쿼리에서 계산해 내려준다 */
    private final Long remainingQty;
    private final InvMovStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime cmplDt;

    public InvMovTaskResponse(Long invMovTaskId, String invMovNo, InvMovDvsn movDvsn, String prodCd, String prodNm,
                              String lotNo, LocalDate expiryDt, String fromLocCd, String toLocCd,
                              Long drctQty, Long cmplQty, Long remainingQty,
                              InvMovStatus status, LocalDateTime createdAt, LocalDateTime cmplDt) {
        this.invMovTaskId = invMovTaskId;
        this.invMovNo = invMovNo;
        this.movDvsn = movDvsn;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.lotNo = lotNo;
        this.expiryDt = expiryDt;
        this.fromLocCd = fromLocCd;
        this.toLocCd = toLocCd;
        this.drctQty = drctQty;
        this.cmplQty = cmplQty;
        this.remainingQty = remainingQty;
        this.status = status;
        this.createdAt = createdAt;
        this.cmplDt = cmplDt;
    }
}
