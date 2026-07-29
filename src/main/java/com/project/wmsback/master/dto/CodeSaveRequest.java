package com.project.wmsback.master.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공통코드 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 그룹 코드는 경로변수로 받는다 — 한 번의 저장은 한 그룹 안에서만 일어난다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CodeSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    /** 코드 값. PK의 일부라 등록 후 변경할 수 없다 (신규 행에서만 입력) */
    private String codeCd;

    private String codeNm;
    private Integer srtSeq;
}
