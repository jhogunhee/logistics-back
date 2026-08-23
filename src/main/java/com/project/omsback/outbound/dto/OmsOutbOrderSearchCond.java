package com.project.omsback.outbound.dto;

import com.project.omsback.outbound.entity.OmsOutbStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/** 출고주문 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class OmsOutbOrderSearchCond {

    private String omsOutbNo;
    private String storeNm;
    private List<OmsOutbStatus> status;

    /** 출고유형 · 차량편수 (공통코드 OUTB_TYP · VHCL_FLTNO) */
    private String outbTyp;
    private String vhclFltno;

    /** 출고 예정일 범위 (from ~ to) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
