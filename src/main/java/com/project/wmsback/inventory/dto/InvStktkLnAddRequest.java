package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 조사 라인 수동 추가 요청 (장부에 없는 재고를 실사에서 발견했을 때 · 기초재고 등록).
 * 해당 재고 키의 재고 행이 없으면 전산수량 0으로 라인이 만들어지고, 확정 시 (+)조정으로 재고 행이 생성된다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InvStktkLnAddRequest {

    private Long prodId;
    private Long locId;
    private Long lotId;
}
