package com.project.mdm.prod.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 상품 거래처 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class ProdVndrSearchCond {

    private String prodCd;
    private String prodNm;
    private String vndrCd;
}
