package com.project.mdm.nbr.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DyncKyTypTest {

    @Test
    void NONE은_날짜_기반이_아니고_패턴이_없다() {
        assertFalse(DyncKyTyp.NONE.isDateBased());
        assertNull(DyncKyTyp.NONE.getDyncKyPattern());
    }

    @Test
    void YEAR_MONTH_DAY는_날짜_기반이고_각자의_포맷을_가진다() {
        assertTrue(DyncKyTyp.YEAR.isDateBased());
        assertEquals("yyyy", DyncKyTyp.YEAR.getDyncKyPattern());

        assertTrue(DyncKyTyp.MONTH.isDateBased());
        assertEquals("yyyyMM", DyncKyTyp.MONTH.getDyncKyPattern());

        assertTrue(DyncKyTyp.DAY.isDateBased());
        assertEquals("yyyyMMdd", DyncKyTyp.DAY.getDyncKyPattern());
    }
}
