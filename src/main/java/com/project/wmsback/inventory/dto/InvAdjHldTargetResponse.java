package com.project.wmsback.inventory.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 재고조정 보류 라인 대상 1건 = 화면 1행. 행 단위는 <b>보류 건</b>이다 — 같은 재고 행에 사유가
 * 다른 미해제 보류가 여러 건 병존하므로, 재고 행을 지목하면 어느 건에서 빠지는지 정해지지 않는다.
 * 담을 때 재고 키(prodId·locId·lotId)를 함께 실어 조정 요청이 그대로 쓴다.
 * QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다.
 */
@Getter
public class InvAdjHldTargetResponse {

    private final Long invHldId;
    private final String hldNo;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final Long locId;
    private final String locCd;
    private final Long lotId;
    private final String lotNo;
    private final LocalDate expiryDt;
    /** 조정전수량으로 화면에 표시되는 값 — 보류 건이 아니라 그 재고 행의 보유수량이다 */
    private final Long onHandQty;
    private final Long hldQty;
    private final Long rlzQty;
    /** 미해제 잔량 = 보류 − 해제누계 (파생값). 이 라인이 폐기할 수 있는 상한 */
    private final Long remainingQty;
    /** 보류사유 (공통코드 HLD_RSN) — 「왜 묶였나」가 폐기 판단의 근거라 함께 내려준다 */
    private final String rsnCd;
    private final String rsnDscr;
    private final LocalDateTime createdAt;

    public InvAdjHldTargetResponse(Long invHldId, String hldNo,
                                   Long prodId, String prodCd, String prodNm,
                                   Long locId, String locCd, Long lotId, String lotNo, LocalDate expiryDt,
                                   Long onHandQty, Long hldQty, Long rlzQty, Long remainingQty,
                                   String rsnCd, String rsnDscr, LocalDateTime createdAt) {
        this.invHldId = invHldId;
        this.hldNo = hldNo;
        this.prodId = prodId;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.locId = locId;
        this.locCd = locCd;
        this.lotId = lotId;
        this.lotNo = lotNo;
        this.expiryDt = expiryDt;
        this.onHandQty = onHandQty;
        this.hldQty = hldQty;
        this.rlzQty = rlzQty;
        this.remainingQty = remainingQty;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
        this.createdAt = createdAt;
    }
}
