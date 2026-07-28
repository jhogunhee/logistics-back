package com.project.omsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbStatus;

/**
 * 주문 확정으로 생성된 WMS 입고예정(ASN)의 요약. 주문 목록에 "그래서 창고에선 어디까지 갔나"를
 * 붙여 보여주기 위한 조회 전용 투영이다.
 *
 * OmsIbOrder가 ASN을 필드로 들고 있지 않은 이유: 참조는 ib_order.oms_ib_order_id 한 방향으로만
 * 두고(omsback → wmsback), 역방향은 그때그때 조회해서 붙인다. 양쪽에 서로를 저장하면
 * 한쪽만 갱신되는 순간 어긋난다.
 */
public record AsnRef(Long omsIbOrderId, Long ibOrderId, String ibNo, IbStatus ibStatus) {
}
