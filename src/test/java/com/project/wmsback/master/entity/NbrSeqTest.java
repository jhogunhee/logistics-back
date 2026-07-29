package com.project.wmsback.master.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NbrSeqTest {

    @Test
    void increment은_seq를_1_증가시킨다() {
        NbrSeq row = NbrSeq.builder().ruleCd("PROD_CD").dyncKy("-").seq(3L).build();

        row.increment();

        assertEquals(4L, row.getSeq());
    }
}
