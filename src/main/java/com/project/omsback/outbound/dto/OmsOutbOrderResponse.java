package com.project.omsback.outbound.dto;

import com.project.omsback.outbound.entity.OmsOutbLine;
import com.project.omsback.outbound.entity.OmsOutbOrder;
import com.project.omsback.outbound.entity.OmsOutbStatus;
import com.project.wmsback.outbound.entity.OutbStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class OmsOutbOrderResponse {

    private final Long omsOutbOrderId;
    private final String omsOutbNo;
    private final OmsOutbStatus status;
    private final Long storeId;
    private final String storeCd;
    private final String storeNm;
    /** 출고유형 (공통코드 OUTB_TYP). 표시명은 화면이 /master/codes/OUTB_TYP 으로 얻는다 */
    private final String outbTyp;
    /** 차량편수 (공통코드 VHCL_FLTNO). null = 배차 미정 */
    private final String vhclFltno;
    private final LocalDate expctDe;
    private final String picNm;
    private final String rmk;
    /** 라인 수 (저장값이 아니라 라인에서 파생) */
    private final int lineCount;
    /** 주문 수량 합계 (라인 파생). 전부 출고단위라 그대로 더해도 뜻이 어긋나지 않는다 */
    private final long totalOrderQty;
    private final LocalDateTime cfmDt;
    private final LocalDateTime createdAt;

    /** 확정으로 생성된 WMS 출고주문. 미확정(CREATED) 주문은 전부 null */
    private final Long outbOrderId;
    private final String outbNo;
    private final OutbStatus outbStatus;
    /** 편성된 웨이브 번호. 편성됐으면 확정취소가 막힌다 — 화면이 미리 알려주기 위한 값 */
    private final String wavNo;

    private OmsOutbOrderResponse(OmsOutbOrder order, OutbOrderRef ref) {
        this.omsOutbOrderId = order.getId();
        this.omsOutbNo = order.getOmsOutbNo();
        this.status = order.getStatus();
        this.storeId = order.getStore().getId();
        this.storeCd = order.getStore().getStoreCd();
        this.storeNm = order.getStore().getStoreNm();
        this.outbTyp = order.getOutbTyp();
        this.vhclFltno = order.getVhclFltno();
        this.expctDe = order.getExpctDe();
        this.picNm = order.getPicNm();
        this.rmk = order.getRmk();
        List<OmsOutbLine> lines = order.getLines();
        this.lineCount = lines.size();
        this.totalOrderQty = lines.stream().mapToLong(OmsOutbLine::getOdrQty).sum();
        this.cfmDt = order.getCfmDt();
        this.createdAt = order.getCreatedAt();

        this.outbOrderId = ref != null ? ref.outbOrderId() : null;
        this.outbNo = ref != null ? ref.outbNo() : null;
        this.outbStatus = ref != null ? ref.outbStatus() : null;
        this.wavNo = ref != null ? ref.wavNo() : null;
    }

    public static OmsOutbOrderResponse from(OmsOutbOrder order, OutbOrderRef ref) {
        return new OmsOutbOrderResponse(order, ref);
    }
}
