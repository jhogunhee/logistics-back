package com.project.wmsback.inventory.dto;

import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 재고조사 라인 1건 = 상세 화면 1행. QueryDSL Projections.constructor로 채워진다.
 *
 * 전산수량이 셋인 이유: sysQty(조사 생성 시점 스냅샷) · nowSysQty(지금 이 순간) · cfmSysQty(확정 시점).
 * 확정 전 화면은 sysQty와 nowSysQty를 비교해 「조사 중 변동됨」을 표시하고, 확정 후에는 cfmSysQty가
 * 조정전수량으로 고정된다 — 조정수량 = stktkQty − cfmSysQty.
 */
@Getter
public class InvStktkLnResponse {

    private final Long lnId;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    private final String locCd;
    private final String zonCd;
    private final String lotNo;
    private final LocalDate expiryDt;
    /** 조사 생성 시점 전산수량 (스냅샷) */
    private final Long sysQty;
    /** 현재 전산수량 (재고 행이 없으면 0). 확정 기준값 — 확정 시 다시 읽는다 */
    private final Long nowSysQty;
    /** 현재 예약수량 — 실사수량이 예약+보류보다 적으면 확정이 막힌다 */
    private final Long alocQty;
    /** 현재 보류수량 */
    private final Long hldQty;
    /** 실사수량 (null = 미조사) */
    private final Long stktkQty;
    /** 확정 시점 전산수량 (= 조정전수량). 확정 전에는 null */
    private final Long cfmSysQty;
    private final String rsnCd;
    private final String rsnDscr;

    public InvStktkLnResponse(Long lnId, String prodCd, String prodNm, TmpZon tmpZon, String locCd, String zonCd,
                              String lotNo, LocalDate expiryDt, Long sysQty, Long nowSysQty, Long alocQty, Long hldQty,
                              Long stktkQty, Long cfmSysQty, String rsnCd, String rsnDscr) {
        this.lnId = lnId;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.tmpZon = tmpZon;
        this.locCd = locCd;
        this.zonCd = zonCd;
        this.lotNo = lotNo;
        this.expiryDt = expiryDt;
        this.sysQty = sysQty;
        this.nowSysQty = nowSysQty == null ? 0L : nowSysQty;
        this.alocQty = alocQty == null ? 0L : alocQty;
        this.hldQty = hldQty == null ? 0L : hldQty;
        this.stktkQty = stktkQty;
        this.cfmSysQty = cfmSysQty;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
    }
}
