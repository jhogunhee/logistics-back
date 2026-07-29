package com.project.wmsback.master.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 채번 규칙 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class NbrRuleSearchCond {

    private String ruleCd;
    private String ruleNm;
}
