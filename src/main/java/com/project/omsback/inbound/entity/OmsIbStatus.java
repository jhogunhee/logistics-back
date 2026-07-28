package com.project.omsback.inbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 입고주문(벤더 발주) 상태. CREATED → CONVERTED / CANCELLED
 *
 * CONVERTED는 "WMS 작업문서(입고예정 ASN)로 변환됐다"는 뜻이다. 변환취소하면 다시 CREATED로
 * 돌아와 재변환할 수 있다 — 창고가 아직 손대지 않은 예정을 물리는 건 주문이 죽는 게 아니라
 * 작업지시만 회수하는 일이라서다.
 *
 * 창고 작업 진행(검수/적치)은 여기서 표현하지 않는다 — 변환으로 생성된 ASN의 IbStatus가 담당한다.
 */
@Getter
@RequiredArgsConstructor
public enum OmsIbStatus {
    CREATED("작성"),
    CONVERTED("변환완료"),
    CANCELLED("취소");

    private final String label;
}
