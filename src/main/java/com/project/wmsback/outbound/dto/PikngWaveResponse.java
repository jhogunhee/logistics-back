package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.WaveStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 피킹지시 화면의 웨이브 1행. 수량 넷은 이 웨이브에 편성된 전 라인·전 할당의 합계다.
 *
 * <p>대상은 「할당이 1건 이상 있는 PLANNED」(발행 대상)와 「ISSUED 전부」(확인·취소 대상)다 —
 * 발행된 웨이브도 피킹이 끝나기 전까지 목록에 남아 지시취소(실적 0일 때만)가 가능하다.
 */
public record PikngWaveResponse(
        Long wavId,
        String wavNo,
        WaveStatus status,
        /** 웨이브의 출고예정일 — 편성 가드가 소속 주문의 출고예정일을 하나로 강제하므로 어느 주문의 값이든 같다 */
        LocalDate expctDe,
        LocalDateTime issuedDt,
        long orderCount,
        long odrQty,
        long alocQty,
        /** 미할당 잔량 (odrQty − alocQty). 발행을 막지는 않지만(주문 단위 가드만 있다) 부족 출고의 예고다 */
        long remainQty,
        /** 피킹 완료 수량 합 — 발행 후 진행 확인용 */
        long pikngQty
) {
    public static PikngWaveResponse of(Long wavId, String wavNo, WaveStatus status, LocalDate expctDe,
                                       LocalDateTime issuedDt, long orderCount, long odrQty,
                                       long alocQty, long pikngQty) {
        return new PikngWaveResponse(wavId, wavNo, status, expctDe, issuedDt,
                orderCount, odrQty, alocQty, odrQty - alocQty, pikngQty);
    }
}
