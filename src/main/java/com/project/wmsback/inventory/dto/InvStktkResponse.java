package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.InvStktkStatus;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 재고조사 1건 = 목록 1행. QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다.
 * 진행도(입력 라인 수)는 상태가 아니라 수량 파생이다 — 「부분입력」 같은 상태를 두지 않는다.
 */
@Getter
public class InvStktkResponse {

    private final Long invStktkId;
    private final String stktkNo;
    /** 조사 범위 (null이면 조건 없음) */
    private final String zonCd;
    private final String locCd;
    private final String prodCd;
    private final String prodNm;
    private final InvStktkStatus status;
    /** 조사 라인 수 */
    private final Long lnCnt;
    /** 실사수량이 입력된 라인 수 (진행도 — lnCnt와 비교해 파생) */
    private final Long cntdCnt;
    private final LocalDateTime createdAt;
    private final LocalDateTime cfmDt;

    public InvStktkResponse(Long invStktkId, String stktkNo, String zonCd, String locCd, String prodCd, String prodNm,
                            InvStktkStatus status, Long lnCnt, Long cntdCnt, LocalDateTime createdAt, LocalDateTime cfmDt) {
        this.invStktkId = invStktkId;
        this.stktkNo = stktkNo;
        this.zonCd = zonCd;
        this.locCd = locCd;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.status = status;
        this.lnCnt = lnCnt;
        this.cntdCnt = cntdCnt;
        this.createdAt = createdAt;
        this.cfmDt = cfmDt;
    }
}
