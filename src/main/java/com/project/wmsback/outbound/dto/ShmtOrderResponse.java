package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbStatus;

import java.time.LocalDateTime;

/**
 * 출고확정 화면의 주문 행. 수량 셋은 라인·할당 집계다(주문 헤더에 수량 컬럼이 없다).
 *
 * @param shotgeQty 결품 = 주문수량 − 피킹수량. 할당에서 못 채운 것(주문 − 할당)과 집품에서 못 채운 것
 *                  (할당 − 피킹)을 합친 값이다 — 확정 뒤에도 파생 가능하므로 컬럼으로 두지 않는다
 * @param shippable 이 행을 지금 확정할 수 있는가 — PICKED 또는 CREATED. 화면의 체크 가능 여부와
 *                  서비스 가드({@code OutbOrder.ship()})가 같은 판정을 쓴다
 */
public record ShmtOrderResponse(
        Long outbOrderId,
        String outbNo,
        String storeNm,
        OutbStatus status,
        long odrQty,
        long alocQty,
        long pikngQty,
        long shotgeQty,
        LocalDateTime shmtDt,
        boolean shippable
) {
    public static ShmtOrderResponse of(Long outbOrderId, String outbNo, String storeNm, OutbStatus status,
                                       long odrQty, long alocQty, long pikngQty, LocalDateTime shmtDt) {
        boolean shippable = status == OutbStatus.PICKED || status == OutbStatus.CREATED;
        return new ShmtOrderResponse(outbOrderId, outbNo, storeNm, status,
                odrQty, alocQty, pikngQty, odrQty - pikngQty, shmtDt, shippable);
    }
}
