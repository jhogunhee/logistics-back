package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class OutbOrderResponse {

    private final Long outbOrderId;
    private final String outbNo;
    private final OutbStatus status;
    private final Long storeId;
    private final String storeCd;
    private final String storeNm;
    private final LocalDate orderDt;
    /** 편성된 웨이브 (미편성이면 NULL) */
    private final Long waveId;
    private final String waveNo;
    /** 전체 라인 수 (라인에서 파생) */
    private final int lineCount;
    /** 주문 수량 합계 (라인 파생) */
    private final long totalOrderQty;
    private final LocalDateTime createdAt;

    private OutbOrderResponse(OutbOrder order) {
        this.outbOrderId = order.getId();
        this.outbNo = order.getOutbNo();
        this.status = order.getStatus();
        this.storeId = order.getStore().getId();
        this.storeCd = order.getStore().getStoreCd();
        this.storeNm = order.getStore().getStoreNm();
        this.orderDt = order.getOrderDt();
        this.waveId = order.getWave() != null ? order.getWave().getId() : null;
        this.waveNo = order.getWave() != null ? order.getWave().getWaveNo() : null;
        this.lineCount = order.getLines().size();
        this.totalOrderQty = order.getLines().stream().mapToLong(OutbLine::getOrderQty).sum();
        this.createdAt = order.getCreatedAt();
    }

    public static OutbOrderResponse from(OutbOrder order) {
        return new OutbOrderResponse(order);
    }
}
