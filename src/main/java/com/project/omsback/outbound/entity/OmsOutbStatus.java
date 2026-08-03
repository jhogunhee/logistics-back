package com.project.omsback.outbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 출고주문(점포 수주) 상태. CREATED ↔ CONFIRMED
 *
 * CONFIRMED는 "확정돼 WMS 작업문서(출고주문)가 나갔다"는 뜻이다. 확정취소하면 다시 CREATED로
 * 돌아와 재확정할 수 있다 — 아직 웨이브에 담기지도 않은 주문을 물리는 건 주문이 죽는 게 아니라
 * 작업지시만 회수하는 일이라서다.
 *
 * <b>취소 상태를 두지 않는다.</b> 없앨 주문은 지운다 — 확정 전이면 바로, 확정 뒤면 확정취소로
 * 되돌린 뒤에. "지운 것도 아니고 쓰는 것도 아닌" 상태를 두면 목록·집계마다 그 상태를 빼는
 * 필터가 따라붙고, 화면에서는 삭제와 취소 중 무엇을 눌러야 하는지가 매번 애매해진다
 * (입고주문 {@link com.project.omsback.inbound.entity.OmsIbStatus}와 같은 판단).
 *
 * 창고 작업 진행(할당/피킹/출고확정)은 여기서 표현하지 않는다 — 확정으로 생성된 WMS
 * 출고주문의 {@code OutbStatus}가 담당한다.
 */
@Getter
@RequiredArgsConstructor
public enum OmsOutbStatus {
    CREATED("작성"),
    CONFIRMED("확정");

    private final String label;
}
