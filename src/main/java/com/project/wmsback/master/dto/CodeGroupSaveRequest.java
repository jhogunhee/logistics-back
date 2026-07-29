package com.project.wmsback.master.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공통코드 그룹 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * <p>
 * 그룹 코드는 PK이자 코드가 리터럴로 참조하는 값이라 등록 후 바꿀 수 없다 — 신규 행에서만 받는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CodeGroupSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private String grpCd;
    private String grpNm;
    private String description;
}
