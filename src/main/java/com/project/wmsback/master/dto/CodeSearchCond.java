package com.project.wmsback.master.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 공통코드 관리 화면 검색 조건. 그룹 코드는 경로변수로 받으므로 여기 없다. */
@Getter
@Setter
@NoArgsConstructor
public class CodeSearchCond {

    private String codeCd;
    private String codeNm;
}
