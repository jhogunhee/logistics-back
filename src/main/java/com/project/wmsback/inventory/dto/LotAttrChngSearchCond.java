package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Lot 속성 정정 이력 검색 조건 (재고 속성변경 화면 — 변경 이력 탭) */
@Getter
@Setter
@NoArgsConstructor
public class LotAttrChngSearchCond {

    private String prodCd;
    private String prodNm;
    private String lotNo;
    private String rsnCd;

    /** 정정일 From (그날 00:00부터) */
    private LocalDate chngFrom;

    /** 정정일 To (그날을 포함한다 — 서버가 다음날 00:00 미만으로 변환) */
    private LocalDate chngTo;
}
