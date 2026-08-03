package com.project.wmsback.strategy.wave.dto;

import com.project.wmsback.strategy.core.condition.FieldCondition;

import java.util.List;

/**
 * 웨이브 전략 정의 — 저장 요청 본문이자 리비전 스냅샷의 형태.
 * condGrp는 조건그룹의 배열: 그룹끼리 OR, 그룹 안의 조건끼리 AND.
 * 예) [[출고유형 = NRML, 차량편수 = 1], [출고유형 = RTNGS]] = "일반출고 1편" 또는 "반품출고 전부".
 */
public record WavStgyDefinition(
        String stgyNm,
        Integer prty,
        List<List<FieldCondition>> condGrp
) {
}
