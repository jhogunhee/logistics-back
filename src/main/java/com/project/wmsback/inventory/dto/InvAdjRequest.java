package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 재고조정 요청. 화면 편집 그리드의 라인들을 한 번에 실행하며, 전체가 한 트랜잭션이다 —
 * 한 건이라도 검증에 걸리면 전량 롤백된다 (보류 등록·로트변경과 같은 방식).
 *
 * 라인은 재고 키(상품·로케이션·Lot)로 대상을 지목한다 — invId를 쓰지 않는 이유 둘:
 * (1) 정렬·락의 표준이 키다. (2) (+) 조정은 재고 행이 아직 없을 수 있어 id가 존재하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InvAdjRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private Long prodId;
        private Long locId;
        private Long lotId;
        /**
         * 조정수량 — 부호 있음(양수 증가 / 음수 감소), 0 금지.
         * 가용 라인의 감소는 그 행의 가용수량(보유 − 예약 − 보류) 이내,
         * 보류 라인의 감소는 그 보류 건의 미해제 잔량 이내다.
         */
        private Long adjQty;
        /**
         * 소진할 보류 건 (보류 라인). 비우면 가용 라인이다.
         * 지정하면 감소만 허용되고, 조정이 그 건의 해제(사유 ADJ)까지 한 트랜잭션에서 처리한다.
         */
        private Long hldId;
        /** 조정사유 코드 (공통코드 INV_ADJ_RSN) */
        private String rsnCd;
        /** 기타 사유 텍스트. rsnCd = ETC일 때만 필수, 그 외 코드에서는 무시된다 */
        private String rsnDscr;
    }
}
