package com.project.omsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 자동발주 발행 요청 — 벤더 1곳 = 입고주문 1건. 산정 결과를 그대로 보내도 되고 수량을 고쳐 보내도 된다.
 * <p>
 * 여러 벤더를 한 요청으로 보낼 때도 트랜잭션은 벤더 단위다({@code BatchExecutor}) —
 * 한 벤더가 걸려도 나머지는 발행된다. 무인 경로(스케줄러)에는 고치고 다시 누를 사람이 없기 때문이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AtoOdrIssueRequest {

    private Long vendorId;
    private LocalDate expctDe;
    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {

        private Long prodId;
        /** 발주 수량 (입고단위) */
        private Long odrQty;
    }
}
