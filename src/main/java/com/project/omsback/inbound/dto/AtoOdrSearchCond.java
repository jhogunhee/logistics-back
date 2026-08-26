package com.project.omsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 자동발주 산정 검색 조건. 비어 있으면 등록된 상품 거래처 전부가 대상 —
 * 스케줄러는 이 상태로 부르고, 화면은 특정 벤더·상품만 다시 볼 때 채운다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AtoOdrSearchCond {

    private String prodCd;
    private String prodNm;
    private String vndrCd;
}
