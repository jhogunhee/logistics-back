package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 정정 대상 Lot 검색 조건 (재고 속성변경 화면 — 상단 조회).
 * 유통기한 미관리 상품의 Lot은 조건과 무관하게 제외된다 (정정 대상이 아니다 — 정의서 3-3).
 */
@Getter
@Setter
@NoArgsConstructor
public class LotAttrTargetSearchCond {

    private String prodCd;
    private String prodNm;
    private String lotNo;

    /** 유통기한 범위 From (이 날짜 이상) — 임박분 정정 점검용 */
    private LocalDate expiryFrom;

    /** 유통기한 범위 To (이 날짜 이하) */
    private LocalDate expiryTo;

    /** true면 재고가 남아 있는 Lot만 (보유 합계 > 0). 소진된 과거 Lot을 걸러낸다 */
    private Boolean onlyInStock;
}
