package com.project.wmsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 적치지시 로케이션 변경·분할 요청. qty가 잔여수량 미만이면 그만큼 새 지시로 떼어낸다 (null = 잔여 전량) */
@Getter
@Setter
@NoArgsConstructor
public class PutawayChangeLocRequest {

    private Long locId;
    private Long qty;
}
