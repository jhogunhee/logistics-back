package com.project.wmsback.master.service;

import com.project.wmsback.master.entity.DyncKyTyp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbrPatternTest {

    @Test
    void SEQ_토큰이_없으면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("PROD-0001", DyncKyTyp.NONE));
    }

    @Test
    void SEQ_토큰이_2개면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("PROD-{SEQ:4}-{SEQ:2}", DyncKyTyp.NONE));
    }

    @Test
    void 알수없는_토큰이면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("PROD-{DEPT}-{SEQ:4}", DyncKyTyp.NONE));
    }

    @Test
    void DATE_타입인데_날짜_토큰이_없으면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("IB-{SEQ:3}", DyncKyTyp.DATE));
    }

    @Test
    void NONE_타입은_날짜_토큰_없이도_통과() {
        NbrPattern.validate("PROD-{SEQ:4}", DyncKyTyp.NONE);
    }

    @Test
    void DATE_타입은_날짜_토큰_있으면_통과() {
        NbrPattern.validate("IB-{yyyyMMdd}-{SEQ:3}", DyncKyTyp.DATE);
    }

    @Test
    void render이_SEQ를_자릿수만큼_zero_pad() {
        String result = NbrPattern.render("PROD-{SEQ:4}", 7, LocalDate.of(2026, 7, 29));
        assertEquals("PROD-0007", result);
    }

    @Test
    void render이_날짜_토큰을_전달받은_날짜로_치환() {
        String result = NbrPattern.render("IB-{yyyyMMdd}-{SEQ:3}", 12, LocalDate.of(2026, 8, 25));
        assertEquals("IB-20260825-012", result);
    }

    @Test
    void render이_seq가_자릿수를_넘으면_그대로_늘어남() {
        String result = NbrPattern.render("PROD-{SEQ:4}", 12345, LocalDate.of(2026, 7, 29));
        assertEquals("PROD-12345", result);
    }

    @Test
    void DATE_타입인데_yyyyMMdd_없이_yyyy만_있으면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("IB-{yyyy}-{SEQ:3}", DyncKyTyp.DATE));
    }

    @Test
    void ptrn이_null이면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate(null, DyncKyTyp.NONE));
    }
}
