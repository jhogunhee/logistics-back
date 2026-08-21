package com.project.wmsback.outbound.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 피킹 화면의 웨이브 1행 — ISSUED 웨이브의 살아 있는 지시(CANCELLED 제외) 합계.
 * 잔량 0(전량 피킹)이어도 목록에 남는다 — 실적 취소가 없어 작업 여지는 없지만 당일 확인용이다.
 */
public record PickingWaveResponse(
        Long wavId,
        String wavNo,
        LocalDate expctDe,
        LocalDateTime issuedDt,
        long drctQty,
        long cmplQty,
        long remainQty,
        /**
         * 아직 닫히지 않은 지시 건수(DIRECTED이면서 실적이 지시수량에 못 미치는 것) —
         * <b>0이 아니면 화면이 무조건 강조한다.</b> 결품 종결을 강제하는 마감·배치가 없어
         * 잊히면 예약이 무기한 남고 주문이 피킹중에 머문다. 강제 관문 대신 상시 노출로 덮는다.
         */
        long openTaskCount
) {
    public static PickingWaveResponse of(Long wavId, String wavNo, LocalDate expctDe,
                                         LocalDateTime issuedDt, long drctQty, long cmplQty,
                                         long openTaskCount) {
        return new PickingWaveResponse(wavId, wavNo, expctDe, issuedDt,
                drctQty, cmplQty, drctQty - cmplQty, openTaskCount);
    }
}
