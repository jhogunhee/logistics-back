package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 보류 등록 요청. 화면 그리드에서 고른 재고 행들을 한 번에 등록하며,
 * 전체가 한 트랜잭션이다 — 한 건이라도 검증에 걸리면 전량 롤백된다 (이동지시 등록과 같은 방식).
 */
@Getter
@Setter
@NoArgsConstructor
public class InvHldRegisterRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        /** 보류 대상 재고 행 (현재고 조회의 invId) */
        private Long invId;
        private Long qty;
        /** 보류 사유 코드 (공통코드 HLD_RSN) */
        private String rsnCd;
        /** 기타 사유 텍스트. rsnCd = ETC일 때만 필수, 그 외 코드에서는 무시된다 */
        private String rsnDscr;
    }
}
