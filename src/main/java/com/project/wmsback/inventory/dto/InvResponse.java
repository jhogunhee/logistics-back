package com.project.wmsback.inventory.dto;

import com.project.wmsback.master.entity.LocType;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 현재고 스냅샷 1건(SKU+Loc+Lot) = 화면 1행.
 * QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다 (가용수량은 쿼리에서 onHand-alloc으로 계산).
 */
@Getter
public class InvResponse {

    private final Long invId;
    private final String skuCd;
    private final String skuNm;
    private final TempZone tempZone;
    private final String locCd;
    private final String zoneCd;
    private final LocType locType;
    private final String lotNo;
    private final LocalDate expiryDt;
    private final Long onHandQty;
    private final Long allocQty;
    /** 가용재고 = 보유 - 할당 (파생값). 쿼리에서 계산해 내려준다 */
    private final Long availableQty;

    public InvResponse(Long invId, String skuCd, String skuNm, TempZone tempZone,
                       String locCd, String zoneCd, LocType locType, String lotNo, LocalDate expiryDt,
                       Long onHandQty, Long allocQty, Long availableQty) {
        this.invId = invId;
        this.skuCd = skuCd;
        this.skuNm = skuNm;
        this.tempZone = tempZone;
        this.locCd = locCd;
        this.zoneCd = zoneCd;
        this.locType = locType;
        this.lotNo = lotNo;
        this.expiryDt = expiryDt;
        this.onHandQty = onHandQty;
        this.allocQty = allocQty;
        this.availableQty = availableQty;
    }
}
