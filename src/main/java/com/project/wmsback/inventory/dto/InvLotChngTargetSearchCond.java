package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 로트변경 대상 재고 행 조회 조건. 보관 로케이션 + 유통기한 관리 상품 + 가용수량 > 0은
 * 조건과 무관하게 서버가 강제한다 (기존 속성변경이 미관리 상품 Lot을 강제 제외하는 것과 같다).
 */
@Getter
@Setter
@NoArgsConstructor
public class InvLotChngTargetSearchCond {

    private String prodCd;
    private String prodNm;
    private String lotNo;
    private String locCd;
}
