package com.project.wmsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 적치 실행 요청 */
@Getter
@Setter
@NoArgsConstructor
public class PutawayRequest {

    /** 적치할 Lot (목록에서 선택한 배치) */
    private Long lotId;
    /** 적치할 수량 (해당 배치의 미적치 잔량 이하) */
    private Long qty;
    /** 대상 보관 로케이션 */
    private Long targetLocId;
}