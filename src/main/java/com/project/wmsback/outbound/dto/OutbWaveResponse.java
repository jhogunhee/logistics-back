package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WaveStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class OutbWaveResponse {

    private final Long outbWaveId;
    private final String waveNo;
    private final WaveStatus status;
    /** 편성된 주문 수 (orders 매핑에서 파생) */
    private final int orderCount;
    private final LocalDateTime releasedAt;
    private final LocalDateTime createdAt;

    private OutbWaveResponse(OutbWave wave) {
        this.outbWaveId = wave.getId();
        this.waveNo = wave.getWaveNo();
        this.status = wave.getStatus();
        this.orderCount = wave.getOrders().size();
        this.releasedAt = wave.getReleasedAt();
        this.createdAt = wave.getCreatedAt();
    }

    public static OutbWaveResponse from(OutbWave wave) {
        return new OutbWaveResponse(wave);
    }
}
