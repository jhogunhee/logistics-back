package com.project.wmsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 검수 저장 요청. 수량은 이번 검수분(증분)이며 서버가 라인 누계에 더하고 전량 재고로 잡는다.
 * (실무 검수는 개수 대조 수준 — 정상 입고는 불합격 수량을 두지 않고 반품입고만 양품/불량을 나눈다,
 *  Lot 번호 채번(입고일 기반)과 유통기한 계산(제조일 + Prod.shelfLifeDays)은 서버 책임이다)
 */
@Getter
@Setter
@NoArgsConstructor
public class ReceiveRequest {

    private List<Line> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Line {
        private Long ibLineId;
        /**
         * 이번 검수수량 — <b>입고단위(발주단위) 개수</b>다 (박스로 오면 박스 수).
         * 낱개(EA) 환산(Prod.toEaQty)과 예정 잔량 초과 검증은 서버 책임. 입고단위 정수만 허용
         */
        private Long inspectQty;
        /** 입고일자 (실제 입고된 날). 소급 등록 대비 라인별 입력, 비우면 오늘 */
        private LocalDate receiptDt;
        /** 제조일자. 유통기한 관리 상품만 필수 — 유통기한 = 제조일 + shelfLifeDays */
        private LocalDate mfgDt;
        /** 이번 불량수량 — 검수 단위(정상 입고단위 · 반품 출고단위). 반품입고만. 반품존에 받아 즉시 보류된다 */
        private Long rjctQty;
        /** 불량사유 (공통코드 HLD_RSN) — rjctQty > 0이면 필수 */
        private String rjctRsnCd;
        /** 불량사유 상세 — ETC일 때만 */
        private String rjctRsnDscr;
    }
}
