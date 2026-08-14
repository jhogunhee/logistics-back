package com.project.wmsback.strategy.putaway.dto;

/**
 * 적치 전략 미리보기 요청 (관리자 화면). definition은 화면의 미저장 상태 그대로 (P4).
 * 대상: 실존 배치의 입고 라인(ibLineId) 또는 가상(prodId) 중 하나 + 수량.
 */
public record PtawyPreviewRequest(
        PtawyStgyDefinition definition,
        Long ibLineId,
        Long prodId,
        Long qty
) {
}
