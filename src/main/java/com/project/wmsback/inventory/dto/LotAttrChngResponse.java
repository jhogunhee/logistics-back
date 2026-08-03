package com.project.wmsback.inventory.dto;

import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lot 속성 정정 이력 1건 = 화면 1행 (append-only 로그 조회 전용).
 * 정정 시각 = createdAt, 정정자 = createdBy. 바뀌지 않은 필드는 전/후가 같은 값이다.
 */
@Getter
public class LotAttrChngResponse {

    private final Long chngId;
    private final Long lotId;
    private final String prodCd;
    private final String prodNm;
    private final String lotNo;
    private final LocalDate bfrMfgDt;
    private final LocalDate aftMfgDt;
    private final LocalDate bfrExpiryDt;
    private final LocalDate aftExpiryDt;
    private final String rsnCd;
    private final String rsnDscr;
    private final LocalDateTime createdAt;
    private final String createdBy;

    public LotAttrChngResponse(Long chngId, Long lotId, String prodCd, String prodNm, String lotNo,
                               LocalDate bfrMfgDt, LocalDate aftMfgDt,
                               LocalDate bfrExpiryDt, LocalDate aftExpiryDt,
                               String rsnCd, String rsnDscr,
                               LocalDateTime createdAt, String createdBy) {
        this.chngId = chngId;
        this.lotId = lotId;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.lotNo = lotNo;
        this.bfrMfgDt = bfrMfgDt;
        this.aftMfgDt = aftMfgDt;
        this.bfrExpiryDt = bfrExpiryDt;
        this.aftExpiryDt = aftExpiryDt;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }
}
