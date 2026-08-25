package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbPrgr;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/** 입고예정(ASN) 목록 검색 조건. 비어 있는 조건은 쿼리에서 무시된다. */
@Getter
@Setter
@NoArgsConstructor
public class IbOrderSearchCond {

    private String ibNo;

    /**
     * 진행단계(5단계 파생) 필터. 저장 상태(IbStatus 3값)가 아니라 화면 뱃지와 같은 체계로 거른다 —
     * 저장 컬럼이 아니라 SQL 조건이 될 수 없고, 서비스가 응답 파생 후 거른다.
     */
    private List<IbPrgr> prgr;

    /** 상대처명 (벤더명 또는 점포명 contains) */
    private String vndrNm;

    /** 발주구분 정확일치 — 반품(RTNGS)만 보기 */
    private String odrDvsn;

    /** 입고 예정일 범위 (from ~ to) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
}
