package com.project.wmsback.inbound.dto;

import com.project.wmsback.inventory.entity.InvHist;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 검수 이력(RECEIVE 건) 1건. 검수 취소 대상 선택 화면에서 사용 */
@Getter
public class ReceiptResponse {

    private final Long invHistId;
    /**
     * 어느 입고 라인의 검수인가. 입고건 단위로 이력을 한 그리드에 모아 보여주므로
     * 행이 어느 라인 것인지 화면이 알아야 한다 (라인 단위 조회에서도 같은 값을 담는다).
     */
    private final Long ibLineId;
    private final String prodCd;
    private final String prodNm;
    /** 검수 입력 단위 = 입고단위. 수량을 「n BOX (m)」로 보여주는 재료 */
    private final String inbUomCd;
    /** 입고단위 1개 = 낱개(EA) 몇 개. qty가 낱개라 화면이 이 값으로 나눈다 */
    private final Long inbEaQty;
    private final String lotNo;
    private final LocalDate receiptDt;
    private final LocalDate mfgDt;
    private final Long qty;
    /** 이미 검수취소(ADJUST)됐는지 — true면 화면에서 취소 버튼을 다시 노출하면 안 된다 */
    private final boolean cancelled;
    private final LocalDateTime createdAt;

    private ReceiptResponse(InvHist hist, boolean cancelled) {
        this.invHistId = hist.getId();
        this.ibLineId = hist.getIbLineId();
        this.prodCd = hist.getProd().getProdCd();
        this.prodNm = hist.getProd().getProdNm();
        this.inbUomCd = hist.getProd().getInbUomCd();
        this.inbEaQty = hist.getProd().eaQtyOf(this.inbUomCd);
        this.lotNo = hist.getLot().getLotNo();
        this.receiptDt = hist.getLot().getReceiptDt();
        this.mfgDt = hist.getLot().getMfgDt();
        this.qty = hist.getQty();
        this.cancelled = cancelled;
        this.createdAt = hist.getCreatedAt();
    }

    public static ReceiptResponse from(InvHist hist, boolean cancelled) {
        return new ReceiptResponse(hist, cancelled);
    }
}
