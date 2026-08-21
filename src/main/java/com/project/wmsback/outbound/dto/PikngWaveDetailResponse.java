package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.WaveStatus;

import java.util.List;

/**
 * 피킹지시 화면의 웨이브 상세 — 지시 행 + 아직 지시가 안 나간 할당 + 할당 0건 주문 목록.
 *
 * <p>{@code noAllocOrders}가 비어 있지 않으면 발행이 차단된다(주문 단위 가드) — 라인 목록에는
 * 할당 행이 없는 주문이 아예 나타나지 않으므로, 차단 사유를 별도 목록으로 내려 화면이 설명한다.
 * <b>발행된 웨이브에서도 내려보낸다</b> — 지시취소 뒤 할당해제로 할당이 0건이 된 주문이 여기
 * 나타나고, 그 주문은 재할당 전까지 추가 발행도 막는다.
 */
public record PikngWaveDetailResponse(
        Long wavId,
        String wavNo,
        WaveStatus status,
        /** 발행된 지시(발행 후) 또는 발행 대상 할당(발행 전) */
        List<PikngRowResponse> rows,
        /**
         * 아직 지시가 나가지 않은 할당 = <b>추가 발행 대상</b>. 발행 후에만 채운다 — 발행 전에는
         * {@code rows}가 곧 이 목록이라 비워 보낸다. 「이 웨이브에 아직 안 나간 것이 있다」가 한 화면에서 보이게 한다.
         */
        List<PikngRowResponse> pendingRows,
        List<NoAllocOrder> noAllocOrders
) {
    /** 할당이 0건이라 발행을 막는 주문 */
    public record NoAllocOrder(String outbNo, String storeNm) {
    }
}
