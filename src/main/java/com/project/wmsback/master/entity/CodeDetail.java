package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통코드 상세. 그룹 내 개별 코드와 표시명/정렬 순서.
 * 시드 데이터로만 운용하는 조회 전용 엔티티 (관리 화면은 추후 추가 예정).
 */
@Entity
@Table(name = "code_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(CodeDetailId.class)
public class CodeDetail extends BaseEntity {

    /** 코드 그룹 코드 (예: TEMP_ZONE) */
    @Id
    @Column(name = "group_cd", length = 30)
    private String groupCd;

    /** 코드 값 (예: DRY). 로직에서 리터럴로 참조하므로 변경 금지, 폐기는 use_yn=N */
    @Id
    @Column(name = "code_cd", length = 30)
    private String codeCd;

    /** 코드 표시명 (예: 상온) */
    @Column(name = "code_nm", nullable = false, length = 100)
    private String codeNm;

    /** 화면 표시 정렬 순서 */
    @Column(name = "sort_ord", nullable = false)
    private Integer sortOrd;

    /** 사용 여부. 과거 데이터가 참조하므로 삭제 대신 N 처리 */
    @Column(name = "use_yn", nullable = false)
    private char useYn;
}