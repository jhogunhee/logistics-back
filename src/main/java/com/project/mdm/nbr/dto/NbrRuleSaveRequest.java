package com.project.mdm.nbr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.nbr.entity.DyncKyTyp;
import com.project.mdm.nbr.entity.NbrRule;
import com.project.mdm.nbr.service.NbrPattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * dyncKyTyp은 신규 등록 시에만 쓰인다 — 수정 행에 값이 와도 무시하지 않고
 * 기존 값과 다르면 거부한다(등록 후 변경 불가).
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(코드 중복 · 발급 이력 검사)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class NbrRuleSaveRequest {

    @JsonProperty("_status")
    private String status;

    private String ruleCd;
    private String ruleNm;
    private String prfx;
    private String prfxDlmt;
    private String deDlmt;
    private Integer seqDgt;
    private DyncKyTyp dyncKyTyp;

    /** 신규 행 → 엔티티 */
    public NbrRule toEntity() {
        if (ruleCd == null || ruleCd.isBlank()) {
            throw new IllegalArgumentException("규칙 코드는 필수입니다.");
        }
        requireRuleNm(ruleCd);
        if (dyncKyTyp == null) {
            throw new IllegalArgumentException("동적키유형은 필수입니다: " + ruleCd);
        }
        NbrPattern.validate(prfx, prfxDlmt, deDlmt, seqDgt, dyncKyTyp);
        return NbrRule.builder()
                .ruleCd(ruleCd)
                .ruleNm(ruleNm)
                .prfx(prfx)
                .prfxDlmt(prfxDlmt)
                .deDlmt(deDlmt)
                .seqDgt(seqDgt)
                .dyncKyTyp(dyncKyTyp)
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영. 동적키유형은 바꿀 수 없다 — 이미 발급된 번호의 리셋 단위가 달라진다 */
    public void updateEntity(NbrRule rule) {
        if (dyncKyTyp != null && dyncKyTyp != rule.getDyncKyTyp()) {
            throw new IllegalArgumentException(
                    "동적키유형은 변경할 수 없습니다. 새 규칙으로 등록하세요: " + rule.getRuleCd());
        }
        requireRuleNm(rule.getRuleCd());
        NbrPattern.validate(prfx, prfxDlmt, deDlmt, seqDgt, rule.getDyncKyTyp());
        rule.update(ruleNm, prfx, prfxDlmt, deDlmt, seqDgt);
    }

    private void requireRuleNm(String ruleCd) {
        if (ruleNm == null || ruleNm.isBlank()) {
            throw new IllegalArgumentException("규칙명은 필수입니다: " + ruleCd);
        }
    }
}
