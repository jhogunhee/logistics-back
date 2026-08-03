package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.InvHldStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 보류 건 1건 = 화면 1행. QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다
 * (미해제 잔량은 쿼리에서 hld-rlz로 계산).
 */
@Getter
public class InvHldResponse {

    private final Long invHldId;
    private final String hldNo;
    private final String prodCd;
    private final String prodNm;
    private final String locCd;
    private final String lotNo;
    private final LocalDate expiryDt;
    private final Long hldQty;
    private final Long rlzQty;
    /** 미해제 잔량 = 보류 - 해제누계 (파생값). 쿼리에서 계산해 내려준다 */
    private final Long remainingQty;
    private final String rsnCd;
    private final String rsnDscr;
    private final InvHldStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime rlzDt;

    public InvHldResponse(Long invHldId, String hldNo, String prodCd, String prodNm,
                          String locCd, String lotNo, LocalDate expiryDt,
                          Long hldQty, Long rlzQty, Long remainingQty,
                          String rsnCd, String rsnDscr, InvHldStatus status,
                          LocalDateTime createdAt, LocalDateTime rlzDt) {
        this.invHldId = invHldId;
        this.hldNo = hldNo;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.locCd = locCd;
        this.lotNo = lotNo;
        this.expiryDt = expiryDt;
        this.hldQty = hldQty;
        this.rlzQty = rlzQty;
        this.remainingQty = remainingQty;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
        this.status = status;
        this.createdAt = createdAt;
        this.rlzDt = rlzDt;
    }
}
