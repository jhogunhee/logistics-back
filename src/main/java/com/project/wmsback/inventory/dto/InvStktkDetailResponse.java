package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.InvStktk;
import com.project.wmsback.inventory.entity.InvStktkStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/** 재고조사 상세 (헤더 + 라인). 라인은 QueryDSL 조회 결과를 그대로 싣는다. */
@Getter
public class InvStktkDetailResponse {

    private final Long invStktkId;
    private final String stktkNo;
    private final String zonCd;
    private final String locCd;
    private final String prodCd;
    private final String prodNm;
    private final InvStktkStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime cfmDt;
    private final List<InvStktkLnResponse> lines;

    public InvStktkDetailResponse(InvStktk stktk, List<InvStktkLnResponse> lines) {
        this.invStktkId = stktk.getId();
        this.stktkNo = stktk.getStktkNo();
        this.zonCd = stktk.getZonCd();
        this.locCd = stktk.getLoc() == null ? null : stktk.getLoc().getLocCd();
        this.prodCd = stktk.getProd() == null ? null : stktk.getProd().getProdCd();
        this.prodNm = stktk.getProd() == null ? null : stktk.getProd().getProdNm();
        this.status = stktk.getStatus();
        this.createdAt = stktk.getCreatedAt();
        this.cfmDt = stktk.getCfmDt();
        this.lines = lines;
    }
}
