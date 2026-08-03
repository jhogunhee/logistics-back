package com.project.omsback.outbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 출고주문 라인 등록 요청. {@link OmsOutbOrderSaveRequest#getLines()}에 실려 온다. */
@Getter
@Setter
@NoArgsConstructor
public class OmsOutbLineSaveRequest {

    private Long prodId;

    /** 주문 수량. 출고단위({@code prod.outb_uom_cd}) 기준이라 확정 시 환산 없이 그대로 넘어간다 */
    private Long odrQty;
}
