package com.project.mdm.code.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.code.entity.CodeDetail;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공통코드 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 그룹 코드는 경로변수로 받는다 — 한 번의 저장은 한 그룹 안에서만 일어난다.
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(그룹 존재 · 코드 중복 검사)은 서비스 몫이다.
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

    /** 참조값 3칸. 뜻은 그룹마다 다르다 */
    private String ref1;
    private String ref2;
    private String ref3;

    /** 신규 행 → 엔티티. 그룹 코드는 경로변수라 서비스가 넘긴다 */
    public CodeDetail toEntity(String grpCd) {
        if (codeCd == null || codeCd.isBlank()) {
            throw new IllegalArgumentException("코드는 필수입니다.");
        }
        validateFields(codeCd);
        return CodeDetail.builder()
                .grpCd(grpCd)
                .codeCd(codeCd)
                .codeNm(codeNm)
                .srtSeq(srtSeq)
                .ref1(ref1)
                .ref2(ref2)
                .ref3(ref3)
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영. 코드 값은 PK이자 로직이 리터럴로 참조하는 값이라 바꾸지 않는다 */
    public void updateEntity(CodeDetail code) {
        validateFields(code.getCodeCd());
        code.update(codeNm, srtSeq, ref1, ref2, ref3);
    }

    private void validateFields(String codeCd) {
        if (codeNm == null || codeNm.isBlank()) {
            throw new IllegalArgumentException("코드명은 필수입니다: " + codeCd);
        }
        if (srtSeq == null) {
            throw new IllegalArgumentException("정렬순서는 필수입니다: " + codeCd);
        }
    }
}
