package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** SKU 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class SkuSearchCond {

    private String skuCd;
    private String skuNm;
    private TempZone tempZone;
}