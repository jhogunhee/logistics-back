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
 * dyncKyTyp은 등록 후 바꿀 수 없다 — 수정 행이 다른 값을 들고 오면 조용히 무시하지 않고 거부한다
 * (무시하면 화면은 바뀐 줄 알고 저장이 성공한 것으로 보인다).
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

    /**
     * 수정 행 → 기존 엔티티에 반영. 동적키유형은 바꿀 수 없다 — 이미 발급된 번호의 리셋 단위가
     * 달라지므로 등록 후 변경 불가이고, 화면도 신규 행에서만 연다. 다른 값이 오면 거부한다 — 조용히
     * 반영만 안 하면 화면은 바뀐 줄 알고 저장이 성공한 것으로 보인다.
     */
    public void updateEntity(NbrRule rule) {
        requireRuleNm(rule.getRuleCd());
        if (dyncKyTyp != null && dyncKyTyp != rule.getDyncKyTyp()) {
            throw new IllegalArgumentException("동적키 유형은 등록 후 변경할 수 없습니다: " + ruleCd);
        }
        NbrPattern.validate(prfx, prfxDlmt, deDlmt, seqDgt, rule.getDyncKyTyp());
        rule.update(ruleNm, prfx, prfxDlmt, deDlmt, seqDgt);
    }

    private void requireRuleNm(String ruleCd) {
        if (ruleNm == null || ruleNm.isBlank()) {
            throw new IllegalArgumentException("규칙명은 필수입니다: " + ruleCd);
        }
    }
}
