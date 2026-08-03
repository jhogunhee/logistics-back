package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 실사수량·사유 저장 요청 (작성 중인 조사만). 화면 그리드에서 편집한 라인들을 한 번에 보낸다 —
 * 전체가 한 트랜잭션이라 한 건이라도 검증에 걸리면 전량 롤백된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InvStktkLnSaveRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        /** 조사 라인 ID */
        private Long lnId;
        /** 실사수량. null이면 미조사로 되돌린다 (0 = 실물 없음과 구분) */
        private Long stktkQty;
        /** 조정사유 코드 (공통코드 ADJ_RSN). 차이가 0이 아닌 라인은 확정 시 필수 */
        private String rsnCd;
        /** 기타 사유 텍스트. rsnCd = ETC일 때만 필수, 그 외 코드에서는 무시된다 */
        private String rsnDscr;
    }
}
