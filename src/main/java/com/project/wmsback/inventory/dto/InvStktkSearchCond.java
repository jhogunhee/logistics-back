package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.InvStktkStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** 재고조사 조회 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class InvStktkSearchCond {

    private String stktkNo;
    private InvStktkStatus status;
    private String zonCd;
    private String prodCd;
    /** 조사 생성일 From (해당 일자 00:00부터) */
    private LocalDate fromDe;
    /** 조사 생성일 To (해당 일자 24:00까지) */
    private LocalDate toDe;
}
