package com.project.wmsback.inventory.dto;

import lombok.Getter;

import java.time.LocalDate;

/**
 * 정정 대상 Lot 1건 = 화면 1행.
 *
 * invRowCnt·onHandQty가 이 정정의 **영향 범위**다 — Lot 단위 정정이라 그 Lot을 공유하는
 * 모든 재고 행(로케이션이 달라도)에 일괄 반영되므로, 저장 전에 몇 행 얼마가 걸려 있는지 보여준다.
 */
@Getter
public class LotAttrTargetResponse {

    private final Long lotId;
    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final String lotNo;

    /** 입고일자 — 정정 대상이 아니지만 제조일자 상한(mfgDt <= receiptDt)이라 화면이 알아야 한다 */
    private final LocalDate receiptDt;
    private final LocalDate mfgDt;
    private final LocalDate expiryDt;

    /** 상품의 유통기한 일수. 화면이 제조일자 변경 시 유통기한 기본값을 제안하는 근거 (강제는 아니다 — 정의서 3-4) */
    private final Integer shelfLifeDays;

    /** 이 Lot을 쓰는 재고 행 수 (로케이션이 다르면 다른 행) */
    private final Long invRowCnt;

    /** 이 Lot의 보유 합계 */
    private final Long onHandQty;

    public LotAttrTargetResponse(Long lotId, Long prodId, String prodCd, String prodNm, String lotNo,
                                 LocalDate receiptDt, LocalDate mfgDt, LocalDate expiryDt,
                                 Integer shelfLifeDays, Long invRowCnt, Long onHandQty) {
        this.lotId = lotId;
        this.prodId = prodId;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.lotNo = lotNo;
        this.receiptDt = receiptDt;
        this.mfgDt = mfgDt;
        this.expiryDt = expiryDt;
        this.shelfLifeDays = shelfLifeDays;
        this.invRowCnt = invRowCnt;
        this.onHandQty = onHandQty;
    }
}
