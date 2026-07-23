package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 출고 주문 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class OutbOrderSearchCond {

    private String outbNo;
    private OutbStatus status;
    private Long storeId;

    /** 특정 웨이브 소속 주문만 (웨이브 상세의 편성 목록 조회) */
    private Long waveId;

    /** 웨이브 편성 화면의 후보 필터 — true: 미편성만, false: 편성된 것만, null: 무시 */
    private Boolean unassigned;

    /** 주문일 범위 (from ~ to) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
