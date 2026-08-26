package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.PutawayTaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 적치지시 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class PutawayTaskSearchCond {

    private String ibNo;
    /** 상대처 — 벤더명 또는 점포명 (반품입고는 점포가 상대다). 지시등록의 PutawaySearchCond와 같은 뜻 */
    private String vndrNm;
    private String prodCd;
    private String prodNm;
    private String toLocCd;
    private PutawayTaskStatus status;

    /** 입고(검수)일자 범위 (from ~ to). Lot.receiptDt 기준 — 지시등록 화면과 같은 축 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
