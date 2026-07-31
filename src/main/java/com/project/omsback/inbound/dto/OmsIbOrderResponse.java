package com.project.omsback.inbound.dto;

import com.project.omsback.inbound.entity.OmsIbLine;
import com.project.omsback.inbound.entity.OmsIbOrder;
import com.project.omsback.inbound.entity.OmsIbStatus;
import com.project.wmsback.inbound.entity.IbStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class OmsIbOrderResponse {

    private final Long omsIbOrderId;
    private final String omsIbNo;
    private final OmsIbStatus status;
    private final Long vendorId;
    private final String vndrCd;
    private final String vndrNm;
    private final LocalDate expctDe;
    /** 발주구분 (공통코드 ODR_DVSN). 표시명은 화면이 /master/codes/ODR_DVSN 으로 얻는다 */
    private final String odrDvsn;
    private final String picNm;
    private final String rmk;
    /** 라인 수 (저장값이 아니라 라인에서 파생) */
    private final int lineCount;
    /** 발주 수량 합계 (라인 파생). 라인 간 단위가 섞여도 그냥 합산한다 — 화면도 단위 없이 숫자만 보여준다 */
    private final long totalOrderQty;
    /** 낱개(EA) 환산 수량 합계 (라인 파생). 낱개 기준이라 라인 간 상품이 달라도 합이 깨끗하다 */
    private final long totalCnvrQty;
    private final LocalDateTime cfmDt;
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
        this.odrDvsn = order.getOdrDvsn();
        this.picNm = order.getPicNm();
        this.rmk = order.getRmk();
        List<OmsIbLine> lines = order.getLines();
        this.lineCount = lines.size();
        this.totalOrderQty = lines.stream().mapToLong(OmsIbLine::getOdrQty).sum();
        this.totalCnvrQty = lines.stream()
                .mapToLong(l -> l.getOdrQty() * l.getProd().eaQtyOf(l.getProd().getInbUomCd()))
                .sum();
        this.cfmDt = order.getCfmDt();
        this.createdAt = order.getCreatedAt();

        this.ibOrderId = asn != null ? asn.ibOrderId() : null;
        this.ibNo = asn != null ? asn.ibNo() : null;
        this.ibStatus = asn != null ? asn.ibStatus() : null;
    }

    public static OmsIbOrderResponse from(OmsIbOrder order, AsnRef asn) {
        return new OmsIbOrderResponse(order, asn);
    }
}
