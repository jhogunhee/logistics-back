package com.project.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 화면이 보내는 1부터의 page를 0부터의 offset으로 바꾸는 자리 — 잘못된 값도 여기서 다 걸러야 한다. */
class PageCondTest {

    @Test
    @DisplayName("아무것도 안 보내면 1페이지 · 기본 크기")
    void defaults() {
        PageCond cond = new PageCond();

        assertEquals(1, cond.getPage());
        assertEquals(PageCond.DEFAULT_SIZE, cond.getSize());
        assertEquals(0L, cond.getOffset());
    }

    @Test
    @DisplayName("page는 1부터, offset은 0부터")
    void offsetIsZeroBased() {
        PageCond cond = new PageCond();
        cond.setPage(3);
        cond.setSize(30);

        assertEquals(3, cond.getPage());
        assertEquals(60L, cond.getOffset());
    }

    @Test
    @DisplayName("0·음수 page는 1페이지로 보정한다 — 음수 offset이 나가면 쿼리가 죽는다")
    void pageBelowOneIsClamped() {
        PageCond zero = new PageCond();
        zero.setPage(0);
        PageCond negative = new PageCond();
        negative.setPage(-5);

        assertEquals(1, zero.getPage());
        assertEquals(0L, zero.getOffset());
        assertEquals(1, negative.getPage());
        assertEquals(0L, negative.getOffset());
    }

    @Test
    @DisplayName("size는 1 이상 MAX_SIZE 이하로 깎는다 — size로 페이징을 무력화하지 못하게")
    void sizeIsClamped() {
        PageCond tooLarge = new PageCond();
        tooLarge.setSize(999_999);
        PageCond tooSmall = new PageCond();
        tooSmall.setSize(0);

        assertEquals(PageCond.MAX_SIZE, tooLarge.getSize());
        assertEquals(1, tooSmall.getSize());
    }

    @Test
    @DisplayName("깎인 size가 offset에도 그대로 반영된다")
    void offsetUsesClampedSize() {
        PageCond cond = new PageCond();
        cond.setPage(2);
        cond.setSize(999_999);

        assertEquals((long) PageCond.MAX_SIZE, cond.getOffset());
    }

    @Test
    @DisplayName("페이지가 커도 offset이 int를 넘지 않고 long으로 센다")
    void offsetDoesNotOverflow() {
        PageCond cond = new PageCond();
        cond.setPage(10_000_000);
        cond.setSize(1000);

        assertEquals(9_999_999_000L, cond.getOffset());
    }
}
