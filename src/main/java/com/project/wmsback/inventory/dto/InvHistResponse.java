package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 재고이력 조회 응답. append-only 원장 1건 = 화면 1행.
 * QueryDSL Projections.constructor로 직접 채워지므로(from/to 로케이션까지 한 번에 조회) 생성자가 public이다.
 */
@Getter
public class InvHistResponse {

    private final Long invHistId;
    private final TxTyp txTyp;
    private final String prodCd;
    private final String prodNm;
    private final String locCd;
    private final String zonCd;
    private final TmpZon tmpZon;
    private final String lotNo;
    private final Long qty;
    private final RefDocTyp rfnDocTyp;
    private final String rfnDocNo;
    /** MOVE의 출발/도착 로케이션 코드 (양쪽 다리 모두 동일 값). MOVE가 아니면 둘 다 null */
    private final String fromLocCd;
    private final String toLocCd;
    private final String createdBy;
    private final LocalDateTime createdAt;

    public InvHistResponse(Long invHistId, TxTyp txTyp, String prodCd, String prodNm,
                            String locCd, String zonCd, TmpZon tmpZon, String lotNo, Long qty,
                            RefDocTyp rfnDocTyp, String rfnDocNo, String fromLocCd, String toLocCd,
                            String createdBy, LocalDateTime createdAt) {
        this.invHistId = invHistId;
        this.txTyp = txTyp;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.locCd = locCd;
        this.zonCd = zonCd;
        this.tmpZon = tmpZon;
        this.lotNo = lotNo;
        this.qty = qty;
        this.rfnDocTyp = rfnDocTyp;
        this.rfnDocNo = rfnDocNo;
        this.fromLocCd = fromLocCd;
        this.toLocCd = toLocCd;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }
}
