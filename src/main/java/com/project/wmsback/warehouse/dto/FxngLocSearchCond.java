package com.project.wmsback.warehouse.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 고정 로케이션 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class FxngLocSearchCond {

    private String prodCd;
    private String locCd;
    private String zonCd;
}
