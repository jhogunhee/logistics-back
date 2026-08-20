package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 결품 종결 요청 — 지시 행 × 결품사유. 수량은 받지 않는다 <b>(잔량 전부가 결품)</b> —
 * 일부만 포기하고 일부는 남겨 두는 업무가 없기 때문이다. 남겨 둘 잔량이 있으면 그냥 더 집으면 된다.
 *
 * <p>요청은 <b>한 트랜잭션</b>이다 — 한 행이라도 걸리면 전량 롤백된다 (피킹 실행과 같은 판단).
 */
@Getter
@Setter
@NoArgsConstructor
public class PikngCloseShortRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long pikngTaskId;
        /** 결품 사유 코드 (공통코드 SHOTGE_RSN) */
        private String rsnCd;
        /** 기타 사유 텍스트. rsnCd = ETC일 때만 필수, 그 외 코드에서는 무시된다 */
        private String rsnDscr;
    }
}
