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
        long remainQty
) {
    public static PickingWaveResponse of(Long wavId, String wavNo, LocalDate expctDe,
                                         LocalDateTime issuedDt, long drctQty, long cmplQty) {
        return new PickingWaveResponse(wavId, wavNo, expctDe, issuedDt, drctQty, cmplQty, drctQty - cmplQty);
    }
}
