package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채번 규칙. code_group과 같은 자연키 코드 테이블 — ruleCd 자체가 PK다.
 * dyncKyTyp은 등록 후 변경 불가 (nbr_seq와의 정합성이 깨지므로 update()가 파라미터로 받지 않는다).
 */
@Entity
@Table(name = "nbr_rule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NbrRule extends BaseEntity {

    @Id
    @Column(name = "rule_cd", length = 30)
    private String ruleCd;

    @Column(name = "rule_nm", nullable = false, length = 100)
    private String ruleNm;

    @Column(name = "ptrn", nullable = false, length = 200)
    private String ptrn;

    @Enumerated(EnumType.STRING)
    @Column(name = "dync_ky_typ", nullable = false, length = 10)
    private DyncKyTyp dyncKyTyp;

    /** 사용 여부. 'N'이면 발급 요청 거부 (과거 발급분은 영향 없음) */
    @Column(name = "us_yn", nullable = false, length = 1)
    private String usYn;

    @Builder
    private NbrRule(String ruleCd, String ruleNm, String ptrn, DyncKyTyp dyncKyTyp, String usYn) {
        this.ruleCd = ruleCd;
        this.ruleNm = ruleNm;
        this.ptrn = ptrn;
        this.dyncKyTyp = dyncKyTyp;
        this.usYn = usYn != null ? usYn : "Y";
    }

    public void update(String ruleNm, String ptrn, String usYn) {
        this.ruleNm = ruleNm;
        this.ptrn = ptrn;
        this.usYn = usYn;
    }

    public boolean isUsable() {
        return "Y".equals(usYn);
    }
}
