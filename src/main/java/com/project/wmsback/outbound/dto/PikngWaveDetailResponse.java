package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.WaveStatus;

import java.util.List;

/**
 * 피킹지시 화면의 웨이브 상세 — 지시 대상(발행 전) 또는 지시 행(발행 후) + 할당 0건 주문 목록.
 *
 * <p>{@code noAllocOrders}가 비어 있지 않으면 발행이 차단된다(주문 단위 가드) — 라인 목록에는
 * 할당 행이 없는 주문이 아예 나타나지 않으므로, 차단 사유를 별도 목록으로 내려 화면이 설명한다.
 */
public record PikngWaveDetailResponse(
        Long wavId,
        String wavNo,
        WaveStatus status,
        List<PikngRowResponse> rows,
        List<NoAllocOrder> noAllocOrders
) {
    /** 할당이 0건이라 발행을 막는 주문 */
    public record NoAllocOrder(String outbNo, String storeNm) {
    }
}
