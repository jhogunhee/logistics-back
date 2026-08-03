package com.project.wmsback.outbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 웨이브 편입 출처. 수동 편성은 금지 대상이 아니라 <b>가시화</b> 대상이다 —
 * 전략 조건과 맞지 않는 주문이 웨이브에 들어 있는 상황을 화면이 구분 표시할 수 있게 남긴다.
 * 편입돼 있을 때만 값이 있다 (ck_outb_order_wav_reg).
 */
@Getter
@RequiredArgsConstructor
public enum WavRegTyp {
    STGY("전략 실행"),
    MANUAL("수동 편성");

    private final String label;
}
