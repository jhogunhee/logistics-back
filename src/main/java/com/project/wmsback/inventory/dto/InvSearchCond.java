package com.project.wmsback.inventory.dto;

import com.project.wmsback.warehouse.entity.LocType;
import com.project.mdm.prod.entity.TempZone;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 현재고 조회 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class InvSearchCond {

    private String prodCd;
    private String prodNm;
    private String locCd;
    private String lotNo;
    private TempZone tmpZon;
    private LocType locTyp;
}
