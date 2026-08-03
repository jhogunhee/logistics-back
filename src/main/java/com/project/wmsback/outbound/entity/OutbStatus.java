package com.project.wmsback.outbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 출고 워크플로 상태. 부분할당 여부는 상태가 아니라 라인/할당 수량에서 파생한다.
 * CREATED → ALLOCATED → PICKING → PICKED → SHIPPED
 *
 * <p><b>취소 상태를 두지 않는다 — 없앨 출고주문은 상위 OMS 주문의 확정취소로 행을 지운다.</b>
 * 예전에는 창고 쪽 취소(CANCELLED)와 주문 쪽 확정취소가 함께 있었는데, 같은 「없앤다」를 두
 * 조작이 서로 다른 결과로 처리했다 — 취소는 행을 남기고 주문을 확정으로 두고, 확정취소는
 * 행을 지우고 주문을 작성으로 되돌렸다. 화면에서는 무엇을 눌러야 하는지가 매번 애매했고,
 * 목록·집계에는 취소분을 빼는 필터가 따라붙었다. 입고예정(ASN)이 같은 이유로 CANCELLED를
 * 폐지한 선례를 따라 확정취소 하나로 모았다
 * ({@link com.project.wmsback.inbound.entity.IbStatus} · migration-drop-asn-cancelled.sql).
 *
 * <p>되돌릴 수 있는 구간은 그대로 <b>웨이브 편성 전</b>이다 ({@code OutbOrder.requireRevertible()}).
 * 피킹이 시작된 뒤의 취소를 v1이 지원하지 않는 것도 달라지지 않았다.
 */
@Getter
@RequiredArgsConstructor
public enum OutbStatus {
    CREATED("생성"),
    ALLOCATED("할당"),
    PICKING("피킹중"),
    PICKED("피킹완료"),
    SHIPPED("출고확정");

    private final String label;
}
