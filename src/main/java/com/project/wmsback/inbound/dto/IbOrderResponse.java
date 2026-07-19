package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class IbOrderResponse {

    private final Long ibOrderId;
    private final String ibNo;
    private final IbStatus status;
    private final String vndrNm;
    private final LocalDate expctDt;
    /** 전체 라인 수 (저장값이 아니라 라인에서 파생) */
    private final int lineCount;
    /** 검수(입고)된 라인 수 — rcvdQty > 0 */
    private final int rcvdLineCount;
    /** 예정 수량 합계 (라인 파생) */
    private final long totalExpctQty;
    /** 검수 수량 합계 (라인 파생) */
    private final long totalRcvdQty;
    private final LocalDateTime createdAt;

    private IbOrderResponse(IbOrder order) {
        this.ibOrderId = order.getId();
        this.ibNo = order.getIbNo();
        this.status = order.getStatus();
        this.vndrNm = order.getVndrNm();
        this.expctDt = order.getExpctDt();
        this.lineCount = order.getLines().size();
        this.rcvdLineCount = (int) order.getLines().stream().filter(l -> l.getRcvdQty() > 0).count();
        this.totalExpctQty = order.getLines().stream().mapToLong(IbLine::getExpctQty).sum();
        this.totalRcvdQty = order.getLines().stream().mapToLong(IbLine::getRcvdQty).sum();
        this.createdAt = order.getCreatedAt();
    }

    public static IbOrderResponse from(IbOrder order) {
        return new IbOrderResponse(order);
    }
}
