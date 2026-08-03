package com.project.wmsback.outbound.dto;

import java.time.LocalDate;

/**
 * 웨이브 상세의 라인 1행 — 주문수량 · 할당수량 · <b>잔량</b>.
 *
 * <p>잔량이 이 프로젝트의 「결품」이다. 별도 테이블도 사유코드도 두지 않고
 * {@code odr_qty − SUM(aloc_qty)} 파생값 하나로 보여준다.
 */
public record AllocLineResponse(
        Long outbLineId,
        Long outbOrderId,
        String outbNo,
        String storeCd,
        String storeNm,
        LocalDate expctDe,
        Long prodId,
        String prodCd,
        String prodNm,
        long odrQty,
        long alocQty,
        long remainQty
) {
    /** QueryDSL Projections 용 — 잔량은 저장값이 아니라 파생이므로 여기서 만든다 */
    public AllocLineResponse(Long outbLineId, Long outbOrderId, String outbNo,
                             String storeCd, String storeNm, LocalDate expctDe,
                             Long prodId, String prodCd, String prodNm,
                             long odrQty, long alocQty) {
        this(outbLineId, outbOrderId, outbNo, storeCd, storeNm, expctDe,
                prodId, prodCd, prodNm, odrQty, alocQty, odrQty - alocQty);
    }
}
