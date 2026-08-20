package com.project.wmsback.strategy.wave.dto;

import java.time.LocalDate;

/**
 * 웨이브 전략 미리보기 요청. definition은 화면의 미저장 상태 그대로 (P4) —
 * 저장본 미리보기(/{id}/preview)는 저장된 정의를 서버가 채운다.
 * 대상은 실행과 같은 모집단(미편성 CREATED 주문)이고, 실행과 같은 기준으로
 * 대상 출고예정일 하루가 필수다 — 기간으로 보여주면 실행이 거부할 그림이 된다.
 */
public record WavPreviewRequest(
        WavStgyDefinition definition,
        LocalDate expctDe
) {
}
