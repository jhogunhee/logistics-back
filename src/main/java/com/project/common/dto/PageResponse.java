package com.project.common.dto;

import java.util.List;

/**
 * 서버 페이징 응답. 화면은 rows로 그리드를 그리고 totCnt로 페이지 수를 센다.
 *
 * 요청한 page·size를 되돌려주는 이유는 보정 때문이다 — 상한에 걸려 깎인 size를 화면이 알아야
 * 「몇 건씩 몇 페이지」가 응답과 어긋나지 않는다.
 */
public record PageResponse<T>(List<T> rows, long totCnt, int page, int size) {

    public static <T> PageResponse<T> of(List<T> rows, Long totCnt, PageCond pageCond) {
        return new PageResponse<>(rows, totCnt != null ? totCnt : 0L, pageCond.getPage(), pageCond.getSize());
    }
}
