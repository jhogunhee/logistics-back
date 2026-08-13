package com.project.wmsback.inventory.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 로트변경 실적 1건 = 화면 1행. Lot 번호·날짜는 실행 시점 스냅샷이다 (자기완결 로그 —
 * 이후 목적지 Lot이 또 정정돼도 이 행은 그대로다). QueryDSL Projections.constructor로
 * 직접 채워지므로 생성자가 public이다.
 */
@Getter
public class InvLotChngResponse {

    private final Long invLotChngId;
    private final String lotChngNo;
    private final String prodCd;
    private final String prodNm;
    private final String locCd;
    private final String fromLotNo;
    private final LocalDate fromMfgDt;
    private final LocalDate fromExpiryDt;
    private final String toLotNo;
    private final LocalDate toMfgDt;
    private final LocalDate toExpiryDt;
    private final Long chngQty;
    /** true 분할(목적지 Lot 신규 채번) / false 병합(기존 Lot 합류) */
    private final Boolean toLotNewYn;
    private final String rsnCd;
    private final String rsnDscr;
    private final LocalDateTime createdAt;

    public InvLotChngResponse(Long invLotChngId, String lotChngNo, String prodCd, String prodNm, String locCd,
                              String fromLotNo, LocalDate fromMfgDt, LocalDate fromExpiryDt,
                              String toLotNo, LocalDate toMfgDt, LocalDate toExpiryDt,
                              Long chngQty, Boolean toLotNewYn, String rsnCd, String rsnDscr,
                              LocalDateTime createdAt) {
        this.invLotChngId = invLotChngId;
        this.lotChngNo = lotChngNo;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.locCd = locCd;
        this.fromLotNo = fromLotNo;
        this.fromMfgDt = fromMfgDt;
        this.fromExpiryDt = fromExpiryDt;
        this.toLotNo = toLotNo;
        this.toMfgDt = toMfgDt;
        this.toExpiryDt = toExpiryDt;
        this.chngQty = chngQty;
        this.toLotNewYn = toLotNewYn;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
        this.createdAt = createdAt;
    }
}
