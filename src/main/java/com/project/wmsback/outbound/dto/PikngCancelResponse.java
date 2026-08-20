package com.project.wmsback.outbound.dto;

import java.util.List;

/** 피킹지시 취소 결과 — 웨이브별 취소(CANCELLED 전이)된 지시 건수 */
public record PikngCancelResponse(
        int waveCount,
        int cancelledCount,
        List<WaveResult> waves
) {
    public record WaveResult(String wavNo, int cancelledCount) {
    }
}
