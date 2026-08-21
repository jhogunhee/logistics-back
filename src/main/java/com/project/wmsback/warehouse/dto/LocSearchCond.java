package com.project.wmsback.warehouse.dto;

import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.LocTyp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 로케이션 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class LocSearchCond {

    private String locCd;
    private String zonCd;
    private LocTyp locTyp;
    /** 소속 존의 업무구분 — 피킹존만 골라 보는 용도 (존 조인으로 판정) */
    private BizDvsn bizDvsn;
}
