package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 이동지시 등록 요청. 화면 그리드에서 고른 재고 행들을 한 번에 등록하며,
 * 전체가 한 트랜잭션이다 — 한 건이라도 검증에 걸리면 전량 롤백된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InvMovRegisterRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        /** 이동 대상 재고 행 (현재고 조회의 invId) */
        private Long invId;
        private Long toLocId;
        private Long qty;
    }
}
