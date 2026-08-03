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
    private LocalDate odrDe;
    /** 출고유형 (공통코드 OUTB_TYP). 비우면 NRML(일반출고) */
    private String outbTyp;
    /** 차량편수 (공통코드 VHCL_FLTNO). 비우면 배차 미정 */
    private String vhclFltno;
    private List<LineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class LineRequest {
        private Long prodId;
        private Long odrQty;
    }
}
