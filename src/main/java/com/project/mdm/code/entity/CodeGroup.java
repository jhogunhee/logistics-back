package com.project.mdm.code.entity;

import com.project.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공통코드 그룹. 코드성 값의 묶음 단위다 (TEMP_ZONE · UOM · ODR_DVSN 등).
 * <p>
 * 그룹 코드는 PK이자 코드가 리터럴로 참조하는 값이라 등록 후 바꾸지 않는다 — 이름과 설명만 고친다.
 * 삭제는 하위 코드가 없을 때만 되고, 그 판정은 CodeService가 한다(FK가 없어 DB는 막아주지 않는다).
 * <p>
 * 코드성 테이블이라 PK는 {@code {테이블명}_id} 규칙 대신 자연키를 쓴다 (조회가 항상 코드 기준).
 */
@Entity
@Table(name = "code_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeGroup extends BaseEntity {

    /** 코드 그룹 코드 (예: TEMP_ZONE) */
    @Id
    @Column(name = "grp_cd", length = 30)
    private String grpCd;

    /** 그룹 명 (화면 표시용) */
    @Column(name = "grp_nm", nullable = false, length = 100)
    private String grpNm;

    /** 그룹 설명. 이 그룹을 어느 컬럼이 참조하는지를 적어 둔다 */
    @Column(name = "description", length = 200)
    private String description;

    @Builder
    private CodeGroup(String grpCd, String grpNm, String description) {
        this.grpCd = grpCd;
        this.grpNm = grpNm;
        this.description = description;
    }

    /** 그룹 코드는 수정 대상이 아니다 (PK이자 로직이 리터럴로 참조하는 값) */
    public void update(String grpNm, String description) {
        this.grpNm = grpNm;
        this.description = description;
    }
}
