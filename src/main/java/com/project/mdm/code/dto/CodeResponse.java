package com.project.mdm.code.dto;

import com.project.mdm.code.entity.CodeDetail;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 공통코드 응답. 화면 콤보박스와 공통코드 관리 그리드가 함께 쓴다 —
 * 콤보박스는 codeCd/codeNm만 읽고 나머지는 무시한다.
 */
@Getter
public class CodeResponse {

    private final String grpCd;
    private final String codeCd;
    private final String codeNm;
    private final Integer srtSeq;
    private final String ref1;
    private final String ref2;
    private final String ref3;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private CodeResponse(CodeDetail codeDetail) {
        this.grpCd = codeDetail.getGrpCd();
        this.codeCd = codeDetail.getCodeCd();
        this.codeNm = codeDetail.getCodeNm();
        this.srtSeq = codeDetail.getSrtSeq();
        this.ref1 = codeDetail.getRef1();
        this.ref2 = codeDetail.getRef2();
        this.ref3 = codeDetail.getRef3();
        this.createdBy = codeDetail.getCreatedBy();
        this.createdAt = codeDetail.getCreatedAt();
        this.updatedBy = codeDetail.getUpdatedBy();
        this.updatedAt = codeDetail.getUpdatedAt();
    }

    public static CodeResponse from(CodeDetail codeDetail) {
        return new CodeResponse(codeDetail);
    }
}
