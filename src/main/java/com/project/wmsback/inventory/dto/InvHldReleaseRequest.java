package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 보류 해제 요청. 화면 그리드에서 입력한 보류 건들을 한 번에 해제하며,
 * 전체가 한 트랜잭션이다 — 한 건이라도 검증에 걸리면 전량 롤백된다 (보류 등록과 같은 방식).
 * 건마다 잔량 이내의 부분 해제를 허용한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InvHldReleaseRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        /** 해제할 보류 건 (보류 목록의 invHldId) */
        private Long hldId;
        private Long qty;
        /** 해제 사유 코드 (공통코드 HLD_RLZ_RSN — 등록 사유와 별개 그룹) */
        private String rsnCd;
        /** 기타 사유 텍스트. rsnCd = ETC일 때만 필수, 그 외 코드에서는 무시된다 */
        private String rsnDscr;
    }
}
