package com.project.wmsback.strategy.core.dto;

import com.project.wmsback.strategy.core.entity.StgyRvsn;

import java.time.LocalDateTime;

/** 리비전 목록 행. 스냅샷 본문은 목록에 싣지 않는다 (상세 조회로 분리 — 용량) */
public record RvsnResponse(Long rvsnNo, String savedBy, LocalDateTime savedAt) {

    public static RvsnResponse from(StgyRvsn rvsn) {
        return new RvsnResponse(rvsn.getRvsnNo(), rvsn.getCreatedBy(), rvsn.getCreatedAt());
    }
}
