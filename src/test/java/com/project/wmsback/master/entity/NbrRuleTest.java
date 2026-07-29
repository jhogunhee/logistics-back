package com.project.wmsback.master.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbrRuleTest {

    @Test
    void usYn을_지정하지_않으면_기본값_Y() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();
        assertEquals("Y", rule.getUsYn());
        assertTrue(rule.isUsable());
    }

    @Test
    void usYn_N이면_isUsable_false() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .usYn("N")
                .build();
        assertFalse(rule.isUsable());
    }

    @Test
    void update은_ruleNm_ptrn_usYn만_바꾸고_ruleCd와_dyncKyTyp은_유지() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").ptrn("PROD-{SEQ:4}").dyncKyTyp(DyncKyTyp.NONE)
                .build();

        rule.update("상품 코드(수정)", "PROD-{SEQ:5}", "N");

        assertEquals("PROD_CD", rule.getRuleCd());
        assertEquals(DyncKyTyp.NONE, rule.getDyncKyTyp());
        assertEquals("상품 코드(수정)", rule.getRuleNm());
        assertEquals("PROD-{SEQ:5}", rule.getPtrn());
        assertEquals("N", rule.getUsYn());
    }
}
