package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 피킹(실행) 화면의 웨이브 검색 조건. 대상은 ISSUED 웨이브뿐이라 상태 조건이 없다 */
@Getter
@Setter
@NoArgsConstructor
public class PickingSearchCond {

    private String wavNo;
    private String prodCd;
    private LocalDate expctDeFrom;
    private LocalDate expctDeTo;
}
