package com.project.mdm.store.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 점포 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class StoreSearchCond {

    private String storeCd;
    private String storeNm;
}
