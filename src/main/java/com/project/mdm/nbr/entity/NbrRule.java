package com.project.mdm.nbr.entity;

import com.project.common.entity.BaseEntity;
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
 * 비활성화라는 중간 상태는 없다 — 규칙을 못 쓰게 하려면 물리 삭제한다(NbrRuleService.delete()).
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

    @Column(name = "prfx", nullable = false, length = 20)
    private String prfx;

    @Column(name = "prfx_dlmt", nullable = false, length = 1)
    private String prfxDlmt;

    @Column(name = "de_dlmt", nullable = false, length = 1)
    private String deDlmt;

    @Column(name = "seq_dgt", nullable = false)
    private Integer seqDgt;

    @Enumerated(EnumType.STRING)
    @Column(name = "dync_ky_typ", nullable = false, length = 10)
    private DyncKyTyp dyncKyTyp;

    @Builder
    private NbrRule(String ruleCd, String ruleNm, String prfx, String prfxDlmt, String deDlmt,
                     Integer seqDgt, DyncKyTyp dyncKyTyp) {
        this.ruleCd = ruleCd;
        this.ruleNm = ruleNm;
        this.prfx = prfx;
        this.prfxDlmt = prfxDlmt;
        this.deDlmt = deDlmt;
        this.seqDgt = seqDgt;
        this.dyncKyTyp = dyncKyTyp;
    }

    public void update(String ruleNm, String prfx, String prfxDlmt, String deDlmt, Integer seqDgt) {
        this.ruleNm = ruleNm;
        this.prfx = prfx;
        this.prfxDlmt = prfxDlmt;
        this.deDlmt = deDlmt;
        this.seqDgt = seqDgt;
    }
}
