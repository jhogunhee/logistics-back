package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.CodeDetail;
import lombok.Getter;

@Getter
public class CodeResponse {

    private final String codeCd;
    private final String codeNm;
    private final Integer sortOrd;

    private CodeResponse(CodeDetail codeDetail) {
        this.codeCd = codeDetail.getCodeCd();
        this.codeNm = codeDetail.getCodeNm();
        this.sortOrd = codeDetail.getSortOrd();
    }

    public static CodeResponse from(CodeDetail codeDetail) {
        return new CodeResponse(codeDetail);
    }
}