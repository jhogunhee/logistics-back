package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 보충지시 발행 요청. 전체가 한 트랜잭션 — 한 건이라도 검증에 걸리면 전량 롤백.
 * 항목 형태는 이동지시 등록(InvMovRegisterRequest.Item)과 동일 — 검증 후 그 창구로 위임된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SpmtIssueRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        /** 원천 재고 행 (보충 대상 조회의 sources/assignments invId) */
        private Long invId;
        /** 보충 대상 고정로케이션 */
        private Long toLocId;
        private Long qty;
    }
}
