package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 이동지시 조회 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class InvMovTaskSearchCond {

    private String invMovNo;
    private InvMovDvsn movDvsn;
    private String prodCd;
    private String prodNm;
    private String fromLocCd;
    private String toLocCd;
    private List<InvMovStatus> status;
}
