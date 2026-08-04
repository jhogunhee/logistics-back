package com.project.wmsback.inbound.dto;

import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 적치 대상 배치 (입고 라인, Lot) 단위. 한 라인이 여러 날 나눠 검수돼 Lot이 여러 개일 수 있어
 * 라인이 아니라 (라인, Lot) 조합마다 한 행 — inv_hist를 이 조합으로 집계해 만든다.
 */
@Getter
public class PutawayCandidateResponse {

    private final Long ibLineId;
    private final Long ibOrderId;
    private final String ibNo;
    private final String vndrNm;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    private final Long lotId;
    private final String lotNo;
    private final LocalDate receiptDt;
    private final LocalDate expiryDt;
    /** 스테이징에 남은 미적치 잔량 (지시 발행 여부와 무관한 실물 기준) */
    private final Long pendingQty;
    /**
     * 이 배치에 이미 걸려 있는 미완료 지시 잔량. 쿼리를 하나로 묶지 않고 서비스가 채우는 이유는
     * inv_hist 배치 집계와 putaway_task 집계가 각각 다른 축으로 그룹핑되기 때문이다.
     */
    private Long drctRemainQty;
    /** 아직 지시하지 않은 수량 = pendingQty − drctRemainQty. 지시 등록 화면이 이 값으로 선택 가능 여부를 정한다 */
    private Long unDrctQty;

    public PutawayCandidateResponse(Long ibLineId, Long ibOrderId, String ibNo, String vndrNm,
                                     String prodCd, String prodNm, TmpZon tmpZon,
                                     Long lotId, String lotNo, LocalDate receiptDt, LocalDate expiryDt,
                                     Long pendingQty) {
        this.ibLineId = ibLineId;
        this.ibOrderId = ibOrderId;
        this.ibNo = ibNo;
        this.vndrNm = vndrNm;
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.tmpZon = tmpZon;
        this.lotId = lotId;
        this.lotNo = lotNo;
        this.receiptDt = receiptDt;
        this.expiryDt = expiryDt;
        this.pendingQty = pendingQty;
        // 지시 집계를 붙이기 전 기본값 — 목록이 지시를 모르는 경로(전략 미리보기 등)에서도 값이 비지 않게 한다
        this.drctRemainQty = 0L;
        this.unDrctQty = pendingQty;
    }

    /** 미완료 지시 잔량을 반영해 미지시 수량을 파생시킨다 (PutawayTaskService가 목록 조립 시 호출) */
    public void applyDirectedQty(long directedQty) {
        this.drctRemainQty = directedQty;
        this.unDrctQty = pendingQty - directedQty;
    }
}
