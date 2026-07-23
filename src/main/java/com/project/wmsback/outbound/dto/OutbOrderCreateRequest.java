package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** 출고 주문 등록 요청. 출고번호는 서버가 채번한다 (OB-YYYYMMDD-NNN). */
@Getter
@Setter
@NoArgsConstructor
public class OutbOrderCreateRequest {

    private Long storeId;
    private LocalDate orderDt;
    private List<LineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineRequest {
        private Long skuId;
        private Long orderQty;
    }
}
