package com.project.mdm.nbr.service;

import com.project.mdm.nbr.entity.DyncKyTyp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbrPatternTest {

    @Test
    void prfx가_null이면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate(null, "-", "-", 4, DyncKyTyp.NONE));
    }

    @Test
    void prfx가_빈값이면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("   ", "-", "-", 4, DyncKyTyp.NONE));
    }

    @Test
    void seqDgt가_null이면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("PROD", "-", "-", null, DyncKyTyp.NONE));
    }

    @Test
    void seqDgt가_0이면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("PROD", "-", "-", 0, DyncKyTyp.NONE));
    }

    @Test
    void seqDgt가_10이면_검증_실패() {
        assertThrows(IllegalArgumentException.class,
                () -> NbrPattern.validate("PROD", "-", "-", 10, DyncKyTyp.NONE));
    }

    @Test
    void seqDgt가_1에서_9사이면_통과() {
        NbrPattern.validate("PROD", "-", "-", 4, DyncKyTyp.NONE);
    }

    @Test
    void render이_NONE이면_접두어와_prfxDlmt와_SEQ만_조립() {
        String result = NbrPattern.render("PROD", "-", "-", 4, 7, DyncKyTyp.NONE, LocalDate.of(2026, 7, 29));
        assertEquals("PROD-0007", result);
    }

    @Test
    void render이_DAY면_prfxDlmt_날짜_deDlmt_순으로_조립() {
        String result = NbrPattern.render("IB", "-", "-", 3, 12, DyncKyTyp.DAY, LocalDate.of(2026, 8, 25));
        assertEquals("IB-20260825-012", result);
    }

    @Test
    void render이_MONTH면_yyyyMM을_끼워_조립() {
        String result = NbrPattern.render("IB", "-", "-", 3, 12, DyncKyTyp.MONTH, LocalDate.of(2026, 8, 25));
        assertEquals("IB-202608-012", result);
    }

    @Test
    void render이_YEAR면_yyyy를_끼워_조립() {
        String result = NbrPattern.render("IB", "-", "-", 3, 12, DyncKyTyp.YEAR, LocalDate.of(2026, 8, 25));
        assertEquals("IB-2026-012", result);
    }

    @Test
    void prfxDlmt와_deDlmt가_다르면_각자의_경계에_독립적으로_들어간다() {
        String result = NbrPattern.render("IB", "_", "-", 3, 12, DyncKyTyp.DAY, LocalDate.of(2026, 8, 25));
        assertEquals("IB_20260825-012", result);
    }

    @Test
    void render이_구분자_없음도_지원() {
        String result = NbrPattern.render("PROD", "", "", 4, 7, DyncKyTyp.NONE, LocalDate.of(2026, 7, 29));
        assertEquals("PROD0007", result);
    }

    @Test
    void render이_seq가_seqDgt_자릿수를_넘으면_그대로_늘어남() {
        String result = NbrPattern.render("PROD", "-", "-", 4, 12345, DyncKyTyp.NONE, LocalDate.of(2026, 7, 29));
        assertEquals("PROD-12345", result);
    }
}
