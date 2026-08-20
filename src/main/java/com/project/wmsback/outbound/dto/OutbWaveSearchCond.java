package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.WaveStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 출고 웨이브 목록 검색 조건.
 *
 * <p>출고예정일은 웨이브 컬럼이 아니라 <b>소속 주문에서 파생</b>하므로 EXISTS로 건다
 * (할당·피킹지시·피킹 화면과 같은 형태). 다른 출고 화면들이 전부 기간을 기본 조건으로 두는데
 * 여기만 없으면 웨이브 목록이 생성 이래 전량으로 자라고, 소속 주문까지 함께 읽어 그만큼 무거워진다.
 */
@Getter
@Setter
@NoArgsConstructor
public class OutbWaveSearchCond {

    private String wavNo;
    private WaveStatus status;

    /** 소속 주문의 출고예정일 (파생 조건 — 이 기간에 걸리는 주문이 하나라도 있으면 그 웨이브가 나온다) */
    private LocalDate expctDeFrom;
    private LocalDate expctDeTo;
}
