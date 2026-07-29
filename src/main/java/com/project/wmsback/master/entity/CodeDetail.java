package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통코드 상세. 그룹 내 개별 코드와 표시명/정렬 순서.
 * <p>
 * 그룹 코드와 코드 값이 함께 PK다 — 코드성 테이블이라 대리키를 두지 않는다
 * ({@code docs/schema.sql}의 「{테이블명}_id」 규칙에 대한 의도된 예외).
 */
@Entity
@Table(name = "code_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(CodeDetailId.class)
public class CodeDetail extends BaseEntity {

    /** 코드 그룹 코드 (예: TEMP_ZONE) */
    @Id
    @Column(name = "grp_cd", length = 30)
    private String grpCd;

    /** 코드 값 (예: DRY). 로직에서 리터럴로 참조하므로 변경 금지 */
    @Id
    @Column(name = "code_cd", length = 30)
    private String codeCd;

    /** 코드 표시명 (예: 상온) */
    @Column(name = "code_nm", nullable = false, length = 100)
    private String codeNm;

    /** 화면 표시 정렬 순서 */
    @Column(name = "srt_seq", nullable = false)
    private Integer srtSeq;

    @Builder
    private CodeDetail(String grpCd, String codeCd, String codeNm, Integer srtSeq) {
        this.grpCd = grpCd;
        this.codeCd = codeCd;
        this.codeNm = codeNm;
        this.srtSeq = srtSeq;
    }

    /** 그룹 코드와 코드 값은 PK이자 로직이 리터럴로 참조하는 값이라 수정 대상에서 제외한다 */
    public void update(String codeNm, Integer srtSeq) {
        this.codeNm = codeNm;
        this.srtSeq = srtSeq;
    }
}