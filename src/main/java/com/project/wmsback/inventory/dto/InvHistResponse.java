package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.RefDocType;
import com.project.wmsback.inventory.entity.TxType;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 재고이력 조회 응답. append-only 원장 1건 = 화면 1행.
 * QueryDSL Projections.constructor로 직접 채워지므로(from/to 로케이션까지 한 번에 조회) 생성자가 public이다.
 */
@Getter
public class InvHistResponse {

    private final Long invHistId;
    private final TxType txType;
    private final String skuCd;
    private final String skuNm;
    private final String locCd;
    private final String zoneCd;
    private final TempZone tempZone;
    private final String lotNo;
    private final Long qty;
    private final RefDocType refDocType;
    private final String refDocNo;
    /** MOVE의 출발/도착 로케이션 코드 (양쪽 다리 모두 동일 값). MOVE가 아니면 둘 다 null */
    private final String fromLocCd;
    private final String toLocCd;
    private final String createdBy;
    private final LocalDateTime createdAt;

    public InvHistResponse(Long invHistId, TxType txType, String skuCd, String skuNm,
                            String locCd, String zoneCd, TempZone tempZone, String lotNo, Long qty,
                            RefDocType refDocType, String refDocNo, String fromLocCd, String toLocCd,
                            String createdBy, LocalDateTime createdAt) {
        this.invHistId = invHistId;
        this.txType = txType;
        this.skuCd = skuCd;
        this.skuNm = skuNm;
        this.locCd = locCd;
        this.zoneCd = zoneCd;
        this.tempZone = tempZone;
        this.lotNo = lotNo;
        this.qty = qty;
        this.refDocType = refDocType;
        this.refDocNo = refDocNo;
        this.fromLocCd = fromLocCd;
        this.toLocCd = toLocCd;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
