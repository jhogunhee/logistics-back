package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.WavRegTyp;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class OutbOrderResponse {

    private final Long outbOrderId;
    private final String outbNo;
    /** 이 문서를 발생시킨 OMS 출고주문 (화면이 주문으로 되짚어 갈 때 쓴다) */
    private final Long omsOutbOrderId;
    private final OutbStatus status;
    private final String outbTyp;
    private final String vhclFltno;
    private final Long storeId;
    private final String storeCd;
    private final String storeNm;
    private final LocalDate odrDe;
    /** 출고 예정일 — 목록 정렬·기간 검색의 기준이자 웨이브 편성 단위 */
    private final LocalDate expctDe;
    /** 편성된 웨이브 (미편성이면 NULL) */
    private final Long wavId;
    private final String wavNo;
    /** 편입 출처 (STGY 전략 실행 / MANUAL 수동 편성). 미편성이면 NULL */
    private final WavRegTyp wavRegTyp;
    /** 전체 라인 수 (라인에서 파생) */
    private final int lineCount;
    /** 주문 수량 합계 (라인 파생) */
    private final long totalOrderQty;
    private final LocalDateTime createdAt;

    private OutbOrderResponse(OutbOrder order) {
        this.outbOrderId = order.getId();
        this.outbNo = order.getOutbNo();
        this.omsOutbOrderId = order.getOmsOutbOrderId();
        this.status = order.getStatus();
        this.outbTyp = order.getOutbTyp();
        this.vhclFltno = order.getVhclFltno();
        this.storeId = order.getStore().getId();
        this.storeCd = order.getStore().getStoreCd();
        this.storeNm = order.getStore().getStoreNm();
        this.odrDe = order.getOdrDe();
        this.expctDe = order.getExpctDe();
        this.wavId = order.getWave() != null ? order.getWave().getId() : null;
        this.wavNo = order.getWave() != null ? order.getWave().getWavNo() : null;
        this.wavRegTyp = order.getWavRegTyp();
        this.lineCount = order.getLines().size();
        this.totalOrderQty = order.getLines().stream().mapToLong(OutbLine::getOdrQty).sum();
        this.createdAt = order.getCreatedAt();
    }

    public static OutbOrderResponse from(OutbOrder order) {
        return new OutbOrderResponse(order);
    }
}
