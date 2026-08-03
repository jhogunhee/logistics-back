package com.project.omsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbStatus;

/**
 * 주문 확정으로 생성된 WMS 출고주문의 요약. 주문 목록에 "그래서 창고에선 어디까지 갔나"를
 * 붙여 보여주기 위한 조회 전용 투영이다 (입고 쪽 {@code AsnRef}와 같은 역할).
 *
 * OmsOutbOrder가 WMS 문서를 필드로 들고 있지 않은 이유: 참조는 outb_order.oms_outb_order_id
 * 한 방향으로만 두고(omsback → wmsback), 역방향은 그때그때 조회해서 붙인다. 양쪽에 서로를
 * 저장하면 한쪽만 갱신되는 순간 어긋난다.
 *
 * @param wavNo 편성된 웨이브 번호. 미편성이면 null — 확정취소 가능 여부를 화면이 미리 판단하는 근거다
 */
public record OutbOrderRef(Long omsOutbOrderId, Long outbOrderId, String outbNo,
                           OutbStatus outbStatus, String wavNo) {
}
