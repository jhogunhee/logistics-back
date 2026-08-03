package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WaveStatus;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 출고 웨이브 1건. PK 키 이름은 {@code wavId}다 — 주문 응답({@code OutbOrderResponse.wavId})과
 * 컨트롤러 경로변수가 이미 그 이름이라, 웨이브 응답만 {@code outbWaveId}로 내려가면 프론트가 같은
 * 값을 응답마다 다른 키로 읽어야 한다.
 */
@Getter
public class OutbWaveResponse {

    private final Long wavId;
    private final String wavNo;
    private final WaveStatus status;
    /** 편성된 주문 수 (orders 매핑에서 파생) */
    private final int orderCount;
    /** 피킹지시 발행 시각. 미발행(PLANNED)이면 NULL */
    private final LocalDateTime issuedDt;
    /** 이 웨이브를 만든 전략 (NULL = 수동 생성) */
    private final Long wavStgyId;
    private final Long rvsnNo;
    private final LocalDateTime createdAt;

    private OutbWaveResponse(OutbWave wave) {
        this.wavId = wave.getId();
        this.wavNo = wave.getWavNo();
        this.status = wave.getStatus();
        this.orderCount = wave.getOrders().size();
        this.issuedDt = wave.getIssuedDt();
        this.wavStgyId = wave.getWavStgyId();
        this.rvsnNo = wave.getRvsnNo();
        this.createdAt = wave.getCreatedAt();
    }

    public static OutbWaveResponse from(OutbWave wave) {
        return new OutbWaveResponse(wave);
    }
}
