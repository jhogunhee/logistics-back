package com.project.omsback.inbound.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 입고주문 라인 등록 요청. {@link OmsIbOrderSaveRequest#getLines()}에 실려 온다. */
@Getter
@Setter
@NoArgsConstructor
public class OmsIbLineSaveRequest {

    private Long prodId;

    /** 발주 수량. 입고단위({@code prod.inb_uom_cd}) 기준이다 — ASN 변환 시 출고단위로 환산된다 */
    private Long odrQty;
}
