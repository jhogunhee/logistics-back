package com.project.wmsback.inbound.dto;

import com.project.wmsback.inventory.entity.InvHist;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 검수 이력(RECEIVE 건) 1건. 검수 취소 대상 선택 화면에서 사용 */
@Getter
public class ReceiptResponse {

    private final Long invHistId;
    private final String lotNo;
    private final LocalDate receiptDt;
    private final LocalDate mfgDt;
    private final Long qty;
    /** 이미 검수취소(ADJUST)됐는지 — true면 화면에서 취소 버튼을 다시 노출하면 안 된다 */
    private final boolean cancelled;
    private final LocalDateTime createdAt;

    private ReceiptResponse(InvHist hist, boolean cancelled) {
        this.invHistId = hist.getId();
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
