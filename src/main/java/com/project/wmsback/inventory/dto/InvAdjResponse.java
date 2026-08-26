package com.project.wmsback.inventory.dto;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 재고조정 실적 1건 = 화면 1행. 조정후수량은 담지 않고 화면이 {@code adjBfrQty + adjQty}로 만든다 —
 * 파생 가능한 값을 저장 구조에 싣지 않는다는 원칙 그대로다.
 * QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다.
 */
@Getter
public class InvAdjResponse {

    private final Long invAdjId;
    private final String adjNo;
    private final String prodCd;
    private final String prodNm;
    private final String locCd;
    private final String lotNo;
    /** 조정전수량 — 실행 시점에 재고 행 락을 걸고 다시 읽은 보유수량 */
    private final Long adjBfrQty;
    /** 조정수량 (부호 있음) */
    private final Long adjQty;
    /** 소진한 보류 건. null이면 가용 라인 — 화면이 「보류분」 표기를 이 값으로 가른다 */
    private final String hldNo;
    private final String rsnCd;
    private final String rsnDscr;
    private final LocalDateTime createdAt;

    public InvAdjResponse(Long invAdjId, String adjNo, String prodCd, String prodNm,
                          String locCd, String lotNo,
                          Long adjBfrQty, Long adjQty, String hldNo,
                          String rsnCd, String rsnDscr, LocalDateTime createdAt) {
        this.invAdjId = invAdjId;
        this.adjNo = adjNo;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.locCd = locCd;
        this.lotNo = lotNo;
        this.adjBfrQty = adjBfrQty;
        this.adjQty = adjQty;
        this.hldNo = hldNo;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
        this.createdAt = createdAt;
    }
}
