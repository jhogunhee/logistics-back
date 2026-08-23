package com.project.omsback.inbound.dto;

import com.project.omsback.inbound.entity.OmsIbStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/** 입고주문 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class OmsIbOrderSearchCond {

    private String omsIbNo;
    private String vndrNm;
    private List<OmsIbStatus> status;

    /** 입고 예정일 범위 (from ~ to) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
