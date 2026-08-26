package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 재고조정 대상 조회 조건. 가용 라인 대상(재고 행)과 보류 라인 대상(보류 건)이 같은 조건을 쓴다 —
 * 화면의 상단 조회 조건이 하나이고 아래 그리드만 탭으로 갈리기 때문이다.
 * rsnCd는 보류 라인 대상에만 적용된다 (보류사유 HLD_RSN).
 */
@Getter
@Setter
@NoArgsConstructor
public class InvAdjTargetSearchCond {

    private String prodCd;
    private String prodNm;
    private String locCd;
    private String lotNo;
    private String zonCd;
    private String rsnCd;
}
