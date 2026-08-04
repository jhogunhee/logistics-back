package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 적치지시 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class PutawayTaskSearchCond {

    private String ibNo;
    private String prodCd;
    private String prodNm;
    private String toLocCd;
    private PutawayTaskStatus status;
}
