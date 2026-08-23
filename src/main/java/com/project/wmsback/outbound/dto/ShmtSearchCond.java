package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 출고확정 화면의 웨이브 검색조건. 주문 쪽 조건(출고번호 · 점포 · 출고예정일)은 할당 화면과
 * 같은 EXISTS다 — 어느 웨이브를 보여줄지만 정하고 건수는 언제나 웨이브 전체다.
 */
@Getter
@Setter
public class ShmtSearchCond {
    private String wavNo;
    private String outbNo;
    /** 점포 — 팝업에서 고른 식별자라 정확일치다 (코드 부분일치 아니다) */
    private Long storeId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expctDeFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expctDeTo;
}
