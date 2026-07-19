package com.project.wmsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/** ASN 등록 요청. 입고번호는 서버가 채번한다 (IB-YYYYMMDD-NNN). */
@Getter
@Setter
@NoArgsConstructor
public class IbOrderCreateRequest {

    private String vndrNm;
    private LocalDate expctDt;
    private List<LineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineRequest {
        private Long skuId;
        private Long expctQty;
    }
}
