package com.project.wmsback.warehouse.dto;

import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.mdm.prod.entity.TempZone;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 존 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class ZonSearchCond {

    private String zonCd;
    private TempZone tmpZon;
    private BizDvsn bizDvsn;
}
