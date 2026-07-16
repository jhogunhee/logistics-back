package com.project.wmsback.master.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.wmsback.master.entity.TempZone;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 신규 행의 SKU 코드는 클라이언트에서 받지 않는다 — 서버가 시퀀스로 채번한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SkuSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long skuId;
    private String skuNm;
    private TempZone tempZone;
    private Integer shelfLifeDays;
}