package com.project.wmsback.inventory.dto;

import com.project.wmsback.inventory.entity.TxType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 재고이력 조회 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class InvHistSearchCond {

    private String skuCd;
    private String skuNm;
    private String locCd;
    private TxType txType;

    /** 참조문서번호 (입고번호/출고번호) — 특정 문서가 만든 이력만 추적할 때 */
    private String refDocNo;

    /** 발생일시 범위 (from ~ to). createdAt 기준 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
