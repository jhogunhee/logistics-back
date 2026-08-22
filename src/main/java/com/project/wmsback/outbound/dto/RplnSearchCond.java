package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 보충 화면 웨이브 검색 조건 — 피킹 화면과 같은 축(웨이브번호 · 상품 · 출고예정일) */
@Getter
@Setter
@NoArgsConstructor
public class RplnSearchCond {
    private String wavNo;
    private String prodCd;
    private LocalDate expctDeFrom;
    private LocalDate expctDeTo;
}
