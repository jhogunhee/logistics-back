package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.WavRegTyp;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

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
    /** 할당이 한 건이라도 붙은 라인 수 — 목록의 「할당 진행」(할당라인/전체라인) */
    private final int alocLineCount;
    /** 주문 수량 합계 (라인 파생) */
    private final long totalOrderQty;
    /**
     * 할당 수량 합계. <b>저장 컬럼이 아니라 outb_alloc 집계다</b> — 할당/피킹 수량을 라인에
     * 컬럼으로 두지 않는 원칙({@link com.project.wmsback.outbound.entity.OutbLine} 참고)을
     * 응답에서도 지킨다. 그래서 이 값은 생성자가 계산하지 않고 밖에서 받는다.
     */
    private final long totalAlocQty;
    /** 출고확정 시각. 미확정이면 NULL — 출고실적은 이 값 + inv_hist의 SHIP 행이다 */
    private final LocalDateTime shmtDt;
    private final LocalDateTime createdAt;

    private OutbOrderResponse(OutbOrder order, Map<Long, Long> alocQtyByLineId) {
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
        // 할당이 없는 라인은 집계 맵에 키 자체가 없다 (GROUP BY 결과라서) — 0으로 읽는다
        this.alocLineCount = (int) order.getLines().stream()
                .filter(l -> alocQtyByLineId.getOrDefault(l.getId(), 0L) > 0L)
                .count();
        this.totalAlocQty = order.getLines().stream()
                .mapToLong(l -> alocQtyByLineId.getOrDefault(l.getId(), 0L))
                .sum();
        this.shmtDt = order.getShmtDt();
        this.createdAt = order.getCreatedAt();
    }

    /**
     * @param alocQtyByLineId 라인별 할당 합계 (outb_alloc 집계). 할당이 없는 라인은 키가 없다 —
     *                        호출자가 빈 맵을 넘겨도 전부 0으로 채워진다.
     */
    public static OutbOrderResponse from(OutbOrder order, Map<Long, Long> alocQtyByLineId) {
        return new OutbOrderResponse(order, alocQtyByLineId);
    }
}
