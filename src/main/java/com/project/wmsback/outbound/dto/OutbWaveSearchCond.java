package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.WaveStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 출고 웨이브 목록 검색 조건. */
@Getter
@Setter
@NoArgsConstructor
public class OutbWaveSearchCond {

    private String waveNo;
    private WaveStatus status;
}
