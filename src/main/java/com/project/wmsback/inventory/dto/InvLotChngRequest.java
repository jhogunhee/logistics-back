package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 재고 로트변경 요청. 화면 그리드에서 고른 재고 행들에 수량·정정 날짜를 지정해 한 번에 실행하며,
 * 전체가 한 트랜잭션이다 — 한 건이라도 검증에 걸리면 전량 롤백된다 (보류 등록과 같은 방식).
 */
@Getter
@Setter
@NoArgsConstructor
public class InvLotChngRequest {

    private List<Item> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        /** 변경 대상 재고 행 (대상 조회의 invId). 같은 행을 두 번 실을 수 없다 */
        private Long invId;
        /** 변경 수량 — 그 행의 가용수량(보유 - 예약 - 보류) 이내 */
        private Long chngQty;
        /** 정정된 제조일자 — 원 Lot과 반드시 달라야 한다 (같으면 정정할 것이 없다) */
        private LocalDate mfgDt;
        /** 정정된 유통기한 — 화면 입력값 (벤더 인쇄값 정정이 주 사용처, 계산 강제 없음) */
        private LocalDate expiryDt;
        /** 변경 사유 코드 (공통코드 LOT_ATTR_RSN) */
        private String rsnCd;
        /** 기타 사유 텍스트. rsnCd = ETC일 때만 필수, 그 외 코드에서는 무시된다 */
        private String rsnDscr;
    }
}
