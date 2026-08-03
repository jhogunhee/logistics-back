package com.project.mdm.prod.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 단위(상품 포장) 관리 화면 검색 조건 */
@Getter
@Setter
@NoArgsConstructor
public class ProdUomSearchCond {

    private String prodCd;
    private String prodNm;
    /** 단위 코드 정확일치. 화면에서 공통코드 UOM 콤보박스로 고른다 */
    private String uomCd;
}
