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

    /** 발주 수량. 정상은 입고단위, 반품은 출고단위 — 확정 시 낱개(EA)로 환산된다 */
    private Long odrQty;

    /** 반품사유 (RTNGS_RSN). 반품 라인만 필수 */
    private String rsnCd;
    /** 반품사유 상세. ETC일 때만 */
    private String rsnDscr;
}
