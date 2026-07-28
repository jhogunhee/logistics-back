package com.project.omsback.inbound.dto;

import com.project.omsback.inbound.entity.OmsIbLine;
import com.project.omsback.inbound.entity.OmsIbOrder;
import com.project.omsback.inbound.entity.OmsIbStatus;
import com.project.wmsback.inbound.entity.IbStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class OmsIbOrderResponse {

    private final Long omsIbOrderId;
    private final String omsIbNo;
    private final OmsIbStatus status;
    private final Long vendorId;
    private final String vndrCd;
    private final String vndrNm;
    private final LocalDate expctDe;
    /** 라인 수 (저장값이 아니라 라인에서 파생) */
    private final int lineCount;
    /** 발주 수량 합계 (라인 파생) */
    private final long totalOrderQty;
    private final LocalDateTime convertedAt;
    private final LocalDateTime createdAt;

    /** 확정으로 생성된 ASN. 미확정(CREATED)·취소 주문은 전부 null */
    private final Long ibOrderId;
    private final String ibNo;
    private final IbStatus ibStatus;

    private OmsIbOrderResponse(OmsIbOrder order, AsnRef asn) {
        this.omsIbOrderId = order.getId();
        this.omsIbNo = order.getOmsIbNo();
        this.status = order.getStatus();
        this.vendorId = order.getVendor().getId();
        this.vndrCd = order.getVendor().getVndrCd();
        this.vndrNm = order.getVendor().getVndrNm();
        this.expctDe = order.getExpctDe();
        this.lineCount = order.getLines().size();
        this.totalOrderQty = order.getLines().stream().mapToLong(OmsIbLine::getOdrQty).sum();
        this.convertedAt = order.getConvertedAt();
        this.createdAt = order.getCreatedAt();

        this.ibOrderId = asn != null ? asn.ibOrderId() : null;
        this.ibNo = asn != null ? asn.ibNo() : null;
        this.ibStatus = asn != null ? asn.ibStatus() : null;
    }

    public static OmsIbOrderResponse from(OmsIbOrder order, AsnRef asn) {
        return new OmsIbOrderResponse(order, asn);
    }
}
