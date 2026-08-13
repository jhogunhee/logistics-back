package com.project.wmsback.inventory.dto;

import lombok.Getter;

import java.time.LocalDate;

/**
 * 로트변경 대상 재고 행 1건 = 화면 1행. 행 단위가 Lot이 아니라 inv 행(로케이션 포함)인 것이
 * 기존 속성변경 대상 조회(LotAttrTargetResponse — Lot 단위·재고 합계)와 다른 지점이다.
 * QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다.
 */
@Getter
public class InvLotChngTargetResponse {

    private final Long invId;
    private final String prodCd;
    private final String prodNm;
    private final String locCd;
    private final String lotNo;
    private final LocalDate receiptDt;
    private final LocalDate mfgDt;
    private final LocalDate expiryDt;
    /** 유통기한 관리 일수 — 화면이 제조일자 입력 시 유통기한 기본값(mfgDt + shelfLifeDays)을 제안하는 근거 */
    private final Integer shelfLifeDays;
    private final Long onHandQty;
    private final Long alocQty;
    private final Long hldQty;
    /** 가용수량 = 보유 - 예약 - 보류 (파생값). 변경 수량의 상한 */
    private final Long avalQty;

    public InvLotChngTargetResponse(Long invId, String prodCd, String prodNm, String locCd, String lotNo,
                                    LocalDate receiptDt, LocalDate mfgDt, LocalDate expiryDt, Integer shelfLifeDays,
                                    Long onHandQty, Long alocQty, Long hldQty, Long avalQty) {
        this.invId = invId;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.locCd = locCd;
        this.lotNo = lotNo;
        this.receiptDt = receiptDt;
        this.mfgDt = mfgDt;
        this.expiryDt = expiryDt;
        this.shelfLifeDays = shelfLifeDays;
        this.onHandQty = onHandQty;
        this.alocQty = alocQty;
        this.hldQty = hldQty;
        this.avalQty = avalQty;
    }
}
