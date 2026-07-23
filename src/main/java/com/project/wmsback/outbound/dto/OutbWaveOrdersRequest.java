package com.project.wmsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 웨이브에 담을 주문 ID 목록. 웨이브 생성(초기 편성)과 주문 추가에서 공용.
 * 생성 시에는 비어 있어도 되며(빈 웨이브), 이후 추가로 담을 수 있다.
 */
@Getter
@Setter
@NoArgsConstructor
public class OutbWaveOrdersRequest {

    private List<Long> orderIds;
}
