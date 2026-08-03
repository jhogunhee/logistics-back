package com.project.wmsback.inventory.dto;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 보류/해제 실적 1건 = 화면 1행 (append-only 로그 조회 전용 — 두 실적 테이블이 같은 화면 형태를 공유한다).
 * qty는 보류 실적이면 보류수량, 해제 실적이면 해제수량. 실적 시각 = createdAt.
 */
@Getter
public class InvHldAcrstResponse {

    private final Long id;
    private final String hldNo;
    private final String prodCd;
    private final String prodNm;
    private final String locCd;
    private final String lotNo;
    private final Long qty;
    private final String rsnCd;
    private final String rsnDscr;
    private final LocalDateTime createdAt;

    public InvHldAcrstResponse(Long id, String hldNo, String prodCd, String prodNm,
                               String locCd, String lotNo, Long qty,
                               String rsnCd, String rsnDscr, LocalDateTime createdAt) {
        this.id = id;
        this.hldNo = hldNo;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.locCd = locCd;
        this.lotNo = lotNo;
        this.qty = qty;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
        this.createdAt = createdAt;
    }
}
