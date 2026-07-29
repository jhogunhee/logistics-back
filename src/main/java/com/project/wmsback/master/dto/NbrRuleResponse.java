package com.project.wmsback.master.dto;

import com.project.wmsback.master.entity.DyncKyTyp;
import com.project.wmsback.master.entity.NbrRule;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NbrRuleResponse {

    private final String ruleCd;
    private final String ruleNm;
    private final String ptrn;
    private final DyncKyTyp dyncKyTyp;
    private final String usYn;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private NbrRuleResponse(NbrRule rule) {
        this.ruleCd = rule.getRuleCd();
        this.ruleNm = rule.getRuleNm();
        this.ptrn = rule.getPtrn();
        this.dyncKyTyp = rule.getDyncKyTyp();
        this.usYn = rule.getUsYn();
        this.createdBy = rule.getCreatedBy();
        this.createdAt = rule.getCreatedAt();
        this.updatedBy = rule.getUpdatedBy();
        this.updatedAt = rule.getUpdatedAt();
    }

    public static NbrRuleResponse from(NbrRule rule) {
        return new NbrRuleResponse(rule);
    }
}
