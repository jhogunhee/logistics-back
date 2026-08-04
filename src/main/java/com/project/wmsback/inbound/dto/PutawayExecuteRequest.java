package com.project.wmsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 적치 실행 요청. 지시가 이미 (라인, Lot, 로케이션)을 확정해 놨으므로 수량만 받는다
 * (부분 실행 허용 → cmpl_qty 누적).
 */
@Getter
@Setter
@NoArgsConstructor
public class PutawayExecuteRequest {

    private Long qty;
}
