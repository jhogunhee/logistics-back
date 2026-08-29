package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 수동할당 요청 — 사용자가 라인 ↔ 재고를 직접 지정한다.
 *
 * <p><b>검증은 전 행에 대해 수행한다.</b> 첫 행만 보고 통과시키면 나머지 행의 과할당·가용초과가
 * DB 제약({@code ck_inv_qty})까지 내려가고, 그때는 이미 어느 행이 문제인지 알려줄 수 없다.
 * 한 트랜잭션이라 한 행이라도 걸리면 전량 롤백된다 — 통과한 행만 저장되는 부분 성공은 없다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ManualAllocRequest {

    /** 2026-08-29: 경로가 /outbound/waves/{wavId}/... 를 벗어나며 본문 필드로 내려왔다 */
    private Long wavId;

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long outbLineId;
        private Long invId;
        private Long qty;
    }
}
