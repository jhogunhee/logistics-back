package com.project.wmsback.strategy.inspection.rule;

import com.project.wmsback.master.entity.Prod;
import com.project.wmsback.strategy.inspection.repository.InspectionQueryRepository;

import java.time.LocalDate;

/**
 * 규칙 평가 입력. lotQuery는 "이 상품의 기존 재고 최신 제조일자" 같은 조회 포트 —
 * 규칙이 리포지토리를 직접 들지 않아 순수 함수에 가깝게 유지된다 (미리보기와 실행이 같은 코드).
 */
public record InspectionContext(
        Prod prod,
        LocalDate receiptDt,
        LocalDate mfgDt,
        InspectionQueryRepository lotQuery
) {
}
