package com.project.mdm.nbr.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NbrRuleTest {

    @Test
    void update은_ruleNm_prfx_구분자2종_seqDgt만_바꾸고_ruleCd와_dyncKyTyp은_유지() {
        NbrRule rule = NbrRule.builder()
                .ruleCd("PROD_CD").ruleNm("상품 코드").prfx("PROD").prfxDlmt("-").deDlmt("-").seqDgt(4)
                .dyncKyTyp(DyncKyTyp.NONE)
                .build();

        rule.update("상품 코드(수정)", "PRODUCT", "_", "/", 5);

        assertEquals("PROD_CD", rule.getRuleCd());
        assertEquals(DyncKyTyp.NONE, rule.getDyncKyTyp());
        assertEquals("상품 코드(수정)", rule.getRuleNm());
        assertEquals("PRODUCT", rule.getPrfx());
        assertEquals("_", rule.getPrfxDlmt());
        assertEquals("/", rule.getDeDlmt());
        assertEquals(5, rule.getSeqDgt());
    }
}
