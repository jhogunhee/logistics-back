package com.project.wmsback.strategy.allocation.dto;

import java.util.List;

/**
 * 할당 미리보기 요청. {@code definition}은 미저장 정의 경로에서만 채워지고,
 * 저장본 미리보기(`/{id}/preview`)는 서버가 저장본에서 정의를 꺼낸다.
 *
 * <p>{@code wavIds}가 비면 「전략 미적용 상태의 대상 웨이브 전체」가 아니라 <b>오류</b>다 —
 * 미리보기는 결과를 눈으로 확인하는 도구라 대상이 무엇인지 명시돼야 한다.
 */
public record AlocPreviewRequest(
        AlocStgyDefinition definition,
        List<Long> wavIds
) {
}
