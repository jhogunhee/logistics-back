package com.project.wmsback.outbound.dto;

import java.util.List;

/**
 * 출고확정 결과.
 *
 * @param orderCount   확정한 주문 수
 * @param shmtQty      출하 수량 합 — SHIP-STAGE에서 실제로 나간 수량 (= 확정 주문들의 피킹수량 합)
 * @param shotgeQty    결품 수량 합 — 주문수량 − 피킹수량
 * @param noStockCount 전량 미출고로 확정된 주문 수(할당 0건, 재고 처리 없음)
 * @param closedWavNos 이번 확정으로 소속 주문이 전부 닫혀 CLOSED가 된 웨이브
 */
public record ShmtConfirmResponse(
        int orderCount,
        long shmtQty,
        long shotgeQty,
        int noStockCount,
        List<String> closedWavNos
) {
}
