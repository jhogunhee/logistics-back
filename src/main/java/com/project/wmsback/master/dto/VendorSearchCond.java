package com.project.wmsback.master.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 벤더 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class VendorSearchCond {

    private String vndrCd;
    private String vndrNm;

    /** 'Y'/'N'. 비우면 전체 (주문 등록용 조회는 'Y'만 넘긴다) */
    private String useYn;
}
