package com.project.wmsback.outbound.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 보충 화면 웨이브 1행 — 보충지시가 있는 ISSUED 웨이브.
 *
 * @param rplnCount 취소되지 않은 보충지시 건수
 * @param openCount 아직 확정되지 않은(DIRECTED) 건수 — <b>0이 아니면 화면이 무조건 강조한다.</b>
 *                  보충이 안 끝난 피킹지시는 실행이 막히므로, 잊히면 그 주문이 피킹 대기에 머문다
 */
public record RplnWaveResponse(
        Long wavId,
        String wavNo,
        LocalDate expctDe,
        LocalDateTime issuedDt,
        long rplnCount,
        long openCount
) {
}
