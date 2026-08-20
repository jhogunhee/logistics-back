package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 피킹지시 화면의 웨이브 검색 조건.
 *
 * <p>주문 쪽 조건(상품·출고번호·점포·출고예정일)은 할당 화면과 같은 EXISTS다 — 라인이 아니라
 * <b>웨이브를 거른다</b>. 발행 단위가 웨이브라 그 아래만 골라 발행할 수 없기 때문이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PikngTaskSearchCond {

    private String wavNo;
    private String prodCd;
    private String outbNo;
    private String storeCd;
    /** 웨이브 상태 필터 — PLANNED / ISSUED / null(전체) */
    private String status;
    private LocalDate expctDeFrom;
    private LocalDate expctDeTo;
}
