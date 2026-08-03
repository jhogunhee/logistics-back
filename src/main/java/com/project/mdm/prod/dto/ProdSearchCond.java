package com.project.mdm.prod.dto;

import com.project.mdm.prod.entity.TempZone;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 상품 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class ProdSearchCond {

    private String prodCd;
    private String prodNm;
    private TempZone tmpZon;
}