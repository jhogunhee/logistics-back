package com.project.wmsback.outbound.dto;

import java.util.List;

/** 웨이브 하나의 할당 현황 — 라인 목록(주문/할당/잔량)과 할당 레코드 목록. */
public record AllocWaveDetailResponse(
        Long wavId,
        String wavNo,
        List<AllocLineResponse> lines,
        List<AllocRowResponse> allocs
) {
}
