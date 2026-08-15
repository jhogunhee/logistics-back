package com.project.mdm.code.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.code.entity.CodeGroup;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 공통코드 그룹 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * <p>
 * 그룹 코드는 PK이자 코드가 리터럴로 참조하는 값이라 등록 후 바꿀 수 없다 — 신규 행에서만 받는다.
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(그룹 중복 · 하위 코드 검사)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CodeGroupSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private String grpCd;
    private String grpNm;
    private String dscr;

    /** 신규 행 → 엔티티 */
    public CodeGroup toEntity() {
        if (grpCd == null || grpCd.isBlank()) {
            throw new IllegalArgumentException("그룹 코드는 필수입니다.");
        }
        requireGrpNm();
        return CodeGroup.builder()
                .grpCd(grpCd)
                .grpNm(grpNm)
                .dscr(dscr)
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영. 이름과 설명만 고친다 */
    public void updateEntity(CodeGroup group) {
        requireGrpNm();
        group.update(grpNm, dscr);
    }

    private void requireGrpNm() {
        if (grpNm == null || grpNm.isBlank()) {
            throw new IllegalArgumentException("그룹명은 필수입니다: " + grpCd);
        }
    }
}
