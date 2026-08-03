package com.project.wmsback.inventory.dto;

import com.project.wmsback.master.entity.LocType;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 현재고 스냅샷 1건(상품+Loc+Lot) = 화면 1행.
 * QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다 (가용수량은 쿼리에서 onHand-alloc으로 계산).
 */
@Getter
public class InvResponse {

    private final Long invId;
    private final String prodCd;
    private final String prodNm;
    private final TempZone tmpZon;
    private final String locCd;
    private final String zonCd;
    private final LocType locTyp;
    private final String lotNo;
    private final LocalDate expiryDt;
    private final Long onHandQty;
    private final Long alocQty;
    private final Long hldQty;
    /** 가용재고 = 보유 - 예약 - 보류 (파생값). 쿼리에서 계산해 내려준다 */
    private final Long availableQty;

    public InvResponse(Long invId, String prodCd, String prodNm, TempZone tmpZon,
                       String locCd, String zonCd, LocType locTyp, String lotNo, LocalDate expiryDt,
                       Long onHandQty, Long alocQty, Long hldQty, Long availableQty) {
        this.invId = invId;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.tmpZon = tmpZon;
        this.locCd = locCd;
        this.zonCd = zonCd;
        this.locTyp = locTyp;
        this.lotNo = lotNo;
        this.expiryDt = expiryDt;
        this.onHandQty = onHandQty;
        this.alocQty = alocQty;
        this.hldQty = hldQty;
        this.availableQty = availableQty;
    }
}
