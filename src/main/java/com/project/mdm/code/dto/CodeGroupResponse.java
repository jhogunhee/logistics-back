package com.project.mdm.code.dto;

import com.project.mdm.code.entity.CodeGroup;
import lombok.Getter;

/** 공통코드 관리 화면의 그룹 선택 목록 */
@Getter
public class CodeGroupResponse {

    private final String grpCd;
    private final String grpNm;
    private final String dscr;

    private CodeGroupResponse(CodeGroup group) {
        this.grpCd = group.getGrpCd();
        this.grpNm = group.getGrpNm();
        this.dscr = group.getDscr();
    }

    public static CodeGroupResponse from(CodeGroup group) {
        return new CodeGroupResponse(group);
    }
}
