package com.project.mdm.code.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 공통코드 관리 화면 그룹 검색 조건 (그룹코드/그룹명 부분일치) */
@Getter
@Setter
@NoArgsConstructor
public class CodeGroupSearchCond {

    private String grpCd;
    private String grpNm;
}
