package com.project.wmsback.outbound.dto;

import java.util.List;

/**
 * 피킹지시 발행 결과 — 웨이브별 생성된 지시 건수와 짝으로 낸 보충지시 건수.
 * {@code noDestination}은 피킹 로케이션이 없어 <b>이번 발행에서 빠진 할당</b>(출고번호/상품) — 웨이브는
 * 나머지로 발행됐고, 이 할당은 고정 로케이션 등록 뒤 추가 발행으로 나간다.
 */
public record PikngIssueResponse(
        int waveCount,
        int taskCount,
        int rplnCount,
        List<WaveResult> waves
) {
    public record WaveResult(String wavNo, int taskCount, int rplnCount, List<String> noDestination) {
    }
}
