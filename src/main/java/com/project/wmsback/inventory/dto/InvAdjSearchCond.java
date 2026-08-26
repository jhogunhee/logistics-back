package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 재고조정 실적 조회 조건 (append-only 로그 조회) */
@Getter
@Setter
@NoArgsConstructor
public class InvAdjSearchCond {

    private String adjNo;
    private String prodCd;
    private String prodNm;
    private String locCd;
    private String lotNo;
    private String rsnCd;
    /** true 보류분만 / false 가용분만 / null 전체. hld_no의 존재 여부로 판정한다 */
    private Boolean hldOnly;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
