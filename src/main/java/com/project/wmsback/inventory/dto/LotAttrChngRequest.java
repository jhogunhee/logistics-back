package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * Lot 속성 정정 요청 (재고 속성변경 저장).
 *
 * 화면 그리드에서 고친 Lot들을 한 번에 보낸다 — 전체가 한 트랜잭션이라 한 건이라도 검증에 걸리면
 * 전량 롤백된다. 정정은 취소 경로가 없어서, 절반만 반영되고 나머지가 실패하면 되돌리는 방법이
 * 반대 방향 정정뿐이다. 그 상태를 아예 만들지 않으려고 전량 롤백으로 둔다.
 */
@Getter
@Setter
@NoArgsConstructor
public class LotAttrChngRequest {

    private List<Item> items;

    /**
     * 정정 1건 = Lot 1개. 사유는 건마다 따로다 — 같은 요청에 실렸을 뿐 서로 다른 상품·다른 오입력이라
     * 하나로 묶을 근거가 없고, lot_attr_chng도 건별로 사유를 남긴다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {

        /** 정정 대상 Lot */
        private Long lotId;

        /**
         * 정정 후 제조일자. 「바꿀 값」이 아니라 정정 후의 최종 값이다 — 한쪽만 바꿔도 둘 다 실어 보낸다.
         * 배치 재사용 키(상품+입고일자+제조일자)의 일부라 충돌 검증 대상이다.
         */
        private LocalDate mfgDt;

        /** 정정 후 유통기한. 제조일자+shelfLifeDays와 일치할 필요는 없다 (벤더 인쇄값 정정이 주 사용처) */
        private LocalDate expiryDt;

        /** 정정 사유 코드 (공통코드 LOT_ATTR_RSN) */
        private String rsnCd;

        /** 기타 사유 텍스트. rsnCd가 ETC일 때만 필수이고 그 외에는 서버가 무시한다 */
        private String rsnDscr;
    }
}
