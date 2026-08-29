package com.project.wmsback.worker.dto;

import com.project.wmsback.worker.entity.WrkrWorkTyp;

import java.util.Map;

/**
 * 작업자 한 명의 실적 요약 — 그리드 한 행.
 *
 * @param usrNm 계정이 지워진 작업자는 null이다 ({@code usr}은 물리삭제라 이름이 남지 않는다).
 *              그래도 실적은 {@code created_by} 문자열로 남아 있어 행 자체는 나온다
 */
public record WrkrAcrstSummaryResponse(String loginId, String usrNm, long totCnt, long totQty,
                                       Map<WrkrWorkTyp, WrkrAcrstCnt> byWorkTyp) {
}
