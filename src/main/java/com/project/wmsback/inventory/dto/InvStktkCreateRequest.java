package com.project.wmsback.inventory.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 재고조사 생성 요청. 지정한 범위의 보관 재고를 훑어 라인을 만들고 전산수량을 스냅샷한다.
 * 세 조건 모두 비면 전 보관 로케이션이 대상이다 — 특정 재고 하나만 정정하려면 범위를 좁게 잡는다
 * (건별 조정 화면을 따로 두지 않는 대신 이 경로가 그 역할을 겸한다).
 */
@Getter
@Setter
@NoArgsConstructor
public class InvStktkCreateRequest {

    /** 조사 범위 — 존 코드 (선택) */
    private String zonCd;
    /** 조사 범위 — 로케이션 (선택) */
    private Long locId;
    /** 조사 범위 — 상품 (선택) */
    private Long prodId;
}
