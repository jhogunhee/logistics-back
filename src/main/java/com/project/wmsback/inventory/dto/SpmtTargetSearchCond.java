package com.project.wmsback.inventory.dto;

import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 보충 대상 검색 조건. 전부 선택 — 비우면 min 미달 전체 */
@Getter
@Setter
@NoArgsConstructor
public class SpmtTargetSearchCond {

    private String zonCd;
    private String prodCd;
    private String prodNm;
    private String locCd;
    private TmpZon tmpZon;
}
