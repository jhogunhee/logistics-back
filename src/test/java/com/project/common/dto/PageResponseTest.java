package com.project.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageResponseTest {

    @Test
    @DisplayName("응답의 page·size는 보정된 값이다 — 화면이 요청 그대로 믿으면 페이지 수가 어긋난다")
    void echoesClampedPageAndSize() {
        PageCond cond = new PageCond();
        cond.setPage(0);
        cond.setSize(999_999);

        PageResponse<String> response = PageResponse.of(List.of("a"), 1L, cond);

        assertEquals(1, response.page());
        assertEquals(PageCond.MAX_SIZE, response.size());
    }

    @Test
    @DisplayName("totCnt가 null이면 0으로 — 셈 쿼리 결과가 비어도 화면이 깨지지 않게")
    void nullTotCntBecomesZero() {
        PageResponse<String> response = PageResponse.of(List.of(), null, new PageCond());

        assertEquals(0L, response.totCnt());
    }
}
