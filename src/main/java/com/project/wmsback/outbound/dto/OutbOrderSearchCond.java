package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/** 출고 주문 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class OutbOrderSearchCond {

    private String outbNo;
    private List<OutbStatus> status;
    private Long storeId;

    /**
     * 출고유형 · 차량편수 (공통코드 OUTB_TYP · VHCL_FLTNO).
     * 웨이브 편성 조건의 기준값이라 편성 화면이 후보를 이 둘로 좁힌다 —
     * 수동 편성도 결국 「같은 유형·같은 차수」로 묶게 되기 때문이다.
     */
    private String outbTyp;
    private String vhclFltno;

    /** 특정 웨이브 소속 주문만 (웨이브 상세의 편성 목록 조회) */
    private Long wavId;

    /** 웨이브 편성 화면의 후보 필터 — true: 미편성만, false: 편성된 것만, null: 무시 */
    private Boolean unassigned;

    /**
     * 출고 예정일 범위 (from ~ to). 주문일이 아니다 — 웨이브 편성 대상도 이 기준으로 좁힌다.
     * 이름은 다른 출고 화면들과 맞췄다 — 같은 「출고예정일」인데 여기만 파라미터명이 달랐다.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expctDeFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expctDeTo;
}
