package com.project.wmsback.strategy.putaway.dto;

/** 적치 추천 요청 (작업자 화면) — 배치의 Lot과 이번에 적치할 수량 */
public record PutawayRecommendRequest(Long lotId, Long qty) {
}
