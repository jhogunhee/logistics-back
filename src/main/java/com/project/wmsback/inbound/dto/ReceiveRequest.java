package com.project.wmsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 검수 저장 요청. 수량은 이번 검수분(증분)이며 서버가 라인 누계에 더하고 전량 재고로 잡는다.
 * (실무 검수는 개수 대조 수준 — 불합격 수량은 관리하지 않고,
 *  Lot 번호 채번(입고일 기반)과 유통기한 계산(제조일 + SKU.shelfLifeDays)은 서버 책임이다)
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
        /** 이번 검수수량 (개수 확인 완료 수량) */
        private Long inspectQty;
        /** 입고일자 (실제 입고된 날). 소급 등록 대비 라인별 입력, 비우면 오늘 */
        private LocalDate receiptDt;
        /** 제조일자. 유통기한 관리 SKU만 필수 — 유통기한 = 제조일 + shelfLifeDays */
        private LocalDate mfgDt;
    }
}
