package com.project.wmsback.worker.dto;

import com.project.wmsback.worker.entity.WrkrWorkTyp;

import java.time.LocalDate;
import java.util.Map;

/** 일자별 추이 한 점. 작업 종류별 건수까지 함께 준다 (막대를 종류로 쌓는다) */
public record WrkrAcrstDailyResponse(LocalDate workDt, long totCnt, long totQty,
                                     Map<WrkrWorkTyp, WrkrAcrstCnt> byWorkTyp) {
}
