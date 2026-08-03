package com.project.mdm.nbr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.nbr.entity.DyncKyTyp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * dyncKyTyp은 신규 등록 시에만 쓰인다 — 수정 행에 값이 와도 서비스가 무시하지 않고
 * 기존 값과 다르면 거부한다(등록 후 변경 불가).
 */
@Getter
@Setter
@NoArgsConstructor
public class NbrRuleSaveRequest {

    @JsonProperty("_status")
    private String status;

    private String ruleCd;
    private String ruleNm;
    private String prfx;
    private String prfxDlmt;
    private String deDlmt;
    private Integer seqDgt;
    private DyncKyTyp dyncKyTyp;
}
