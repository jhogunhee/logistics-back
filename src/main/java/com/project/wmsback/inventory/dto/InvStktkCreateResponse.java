package com.project.wmsback.inventory.dto;

import lombok.Getter;

/** 조사 생성 결과 — 상세 이동에 쓸 PK와 안내용 조사번호를 함께 돌려준다 */
@Getter
public class InvStktkCreateResponse {

    private final Long invStktkId;
    private final String stktkNo;

    public InvStktkCreateResponse(Long invStktkId, String stktkNo) {
        this.invStktkId = invStktkId;
        this.stktkNo = stktkNo;
    }
}
