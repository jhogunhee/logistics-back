package com.project.common.dto;

import lombok.Setter;

/**
 * 서버 페이징 요청. 화면이 보내는 page는 1부터이고, 0부터 세는 offset으로 바꾸는 자리는 여기 하나다.
 *
 * 검색조건 DTO와 따로 두는 이유 — 조건을 바꿔 조회하면 1페이지로 돌아가야 하는데,
 * 조건 안에 섞으면 조건 하나를 만질 때마다 페이지를 같이 챙겨야 한다(화면의 usePage 훅도 같은 이유로 나눠 든다).
 */
@Setter
public class PageCond {

    public static final int DEFAULT_SIZE = 100;

    /** size로 페이징을 무력화하는 우회를 막는 상한 */
    public static final int MAX_SIZE = 1000;

    private int page = 1;
    private int size = DEFAULT_SIZE;

    // 보정을 getter에 두는 이유 — 화면이 0·음수·과대값을 보내도, 아예 보내지 않아도 같은 규칙 하나를 지난다

    public int getPage() {
        return Math.max(page, 1);
    }

    public int getSize() {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public long getOffset() {
        return (long) (getPage() - 1) * getSize();
    }
}
