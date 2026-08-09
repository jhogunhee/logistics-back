package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 이동확정 요청. 화면 그리드에서 확정수량을 입력한 지시들을 한 번에 확정하며,
 * 전체가 한 트랜잭션이다 — 한 건이라도 검증에 걸리면 전량 롤백된다 (보류 해제와 같은 방식).
 * 지시가 이미 (재고, TO, 수량)을 확정해 놨으므로 건마다 확정수량만 받는다 (부분확정 허용).
 */
@Getter
@Setter
@NoArgsConstructor
public class InvMovConfirmRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        /** 확정할 이동지시 (지시 목록의 invMovTaskId) */
        private Long taskId;
        private Long qty;
    }
}
