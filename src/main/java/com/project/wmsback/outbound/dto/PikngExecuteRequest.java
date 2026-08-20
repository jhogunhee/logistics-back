package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 피킹 실행 요청 — 지시 행 × 수량. 행마다 부분 수량을 허용하고(잔량은 재피킹으로 소진)
 * 요청은 <b>한 트랜잭션</b>이다 — 한 행이라도 걸리면 전량 롤백된다
 * (이동확정의 「다건 일괄 — 전량 롤백, 건마다 부분 허용」과 같은 판단).
 */
@Getter
@Setter
@NoArgsConstructor
public class PikngExecuteRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long pikngTaskId;
        private Long qty;
    }
}
