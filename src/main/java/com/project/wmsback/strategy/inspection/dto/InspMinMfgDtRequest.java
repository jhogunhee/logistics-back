package com.project.wmsback.strategy.inspection.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 검수 입력 전 힌트 요청 — 상품·입고일자 짝 목록. 검수 화면이 라인 전부를 한 번에 묻는다
 * (원격 DB라 라인마다 부르면 왕복이 곧 지연이다). 입고일자를 비우면 오늘로 본다(검수 저장과 같은 기본값).
 */
public record InspMinMfgDtRequest(List<Item> items) {

    public record Item(Long prodId, LocalDate receiptDt) {
    }
}
