package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 피킹(실행) 화면의 웨이브 검색 조건. 대상은 ISSUED 웨이브뿐이라 상태 조건이 없다.
 *
 * <p>상품·로케이션은 지시가 있는 웨이브를 통째로 고른다(EXISTS) — 상단 그리드의 합계는
 * 언제나 웨이브 전체다. 다만 <b>로케이션은 화면이 하단 지시 행까지 함께 좁힌다</b> —
 * 피킹은 하단에서 지시 행을 체크해 실행하므로 작업자를 구역별로 나눠 붙일 수 있기 때문이다
 * (상단에서 웨이브를 체크해 통째로 실행하는 할당·피킹지시 화면과 갈리는 지점).
 */
@Getter
@Setter
@NoArgsConstructor
public class PickingSearchCond {

    private String wavNo;
    private String prodCd;
    /** 지시의 출발 로케이션 — 집품 구역을 나누는 축 */
    private String locCd;
    private LocalDate expctDeFrom;
    private LocalDate expctDeTo;
}
