package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 보류 실적/해제 실적 공용 검색 조건 (두 실적 화면의 조회 축이 같다 — 보류번호·상품·로케이션·Lot) */
@Getter
@Setter
@NoArgsConstructor
public class InvHldAcrstSearchCond {

    private String hldNo;
    private String prodCd;
    private String prodNm;
    private String locCd;
    private String lotNo;
    private String rsnCd;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
