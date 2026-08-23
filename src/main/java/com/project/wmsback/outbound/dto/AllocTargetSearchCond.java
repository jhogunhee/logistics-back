package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 할당 대상 웨이브 검색 조건.
 *
 * <p><b>주문 쪽 조건(상품·출고번호·점포·출고예정일)은 라인이 아니라 웨이브를 거른다</b> —
 * 조건에 맞는 라인이 하나라도 있으면 그 웨이브가 통째로 걸리고, 화면에 뜨는 수량은 언제나
 * 웨이브 전체의 합계다(EXISTS). 할당의 실행 단위가 웨이브라 그 아래만 골라 실행할 수 없기
 * 때문이고, 조건으로 좁힌 합계를 보여주면 <b>실행될 수량과 화면의 수량이 어긋난다.</b>
 *
 * <p>특정 출고번호로 검색해도 결과가 그 웨이브 전체인 것은 의도된 동작이다 —
 * 화면이 하단 라인 그리드에서 일치 행을 강조해 그 사실을 설명한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AllocTargetSearchCond {

    private String wavNo;
    private String prodCd;
    private String outbNo;
    /** 점포 — 팝업에서 고른 식별자라 정확일치다 (코드 부분일치 아니다) */
    private Long storeId;
    /** 출고 예정일 범위 — 웨이브는 「같은 날 나갈 주문」 묶음이라 주문일이 아니라 이쪽을 본다 */
    private LocalDate expctDeFrom;
    private LocalDate expctDeTo;
}
