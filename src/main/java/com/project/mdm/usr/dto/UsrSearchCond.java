package com.project.mdm.usr.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 사용자 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class UsrSearchCond {

    /** 아이디 또는 사용자명 부분일치 */
    private String keyword;
}
