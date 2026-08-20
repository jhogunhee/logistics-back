package com.project.wmsback.outbound.dto;

import java.util.List;

/** 피킹지시 발행 결과 — 웨이브별 생성된 지시 건수 */
public record PikngIssueResponse(
        int waveCount,
        int taskCount,
        List<WaveResult> waves
) {
    public record WaveResult(String wavNo, int taskCount) {
    }
}
