package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 로트변경 실적 조회 조건 (append-only 로그 조회) */
@Getter
@Setter
@NoArgsConstructor
public class InvLotChngSearchCond {

    private String lotChngNo;
    private String prodCd;
    private String prodNm;
    private String locCd;
    /** 원/목적지 어느 쪽이든 매치 — 「이 Lot이 낀 로트변경」을 한 조건으로 찾는다 */
    private String lotNo;
    private String rsnCd;
}
