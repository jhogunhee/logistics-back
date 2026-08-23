package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 피킹지시 화면의 웨이브 검색 조건.
 *
 * <p>조건은 <b>어느 웨이브를 발행할지만 정한다</b> — 웨이브번호·상태·출고예정일 셋이다.
 * 주문 쪽 축(출고번호·상품·점포)은 두지 않는다 — 발행 대상을 화면에서 선별할 수 없고
 * 「선택 웨이브의 미지시 잔량 전량」이 대상이라, 그 축들이 답하는 질문은 「어느 웨이브냐」
 * 하나뿐이다. 웨이브번호가 이미 그 답이다.
 *
 * <p>라인 단위로 대상을 찾는 할당 화면과 갈리는 지점이다 — 그쪽은 수기할당이 라인 단위
 * 업무라 주문 쪽 축과 하단 강조가 짝을 이룬다.
 */
@Getter
@Setter
@NoArgsConstructor
public class PikngTaskSearchCond {

    private String wavNo;
    /** 웨이브 상태 필터 — PLANNED / ISSUED / null(전체) */
    private String status;
    private LocalDate expctDeFrom;
    private LocalDate expctDeTo;
}
