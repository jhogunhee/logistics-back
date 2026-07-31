package com.project.omsback.inbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 입고주문(벤더 발주) 상태. CREATED ↔ CONFIRMED
 *
 * CONFIRMED는 "확정돼 WMS 작업문서(입고예정 ASN)가 나갔다"는 뜻이다. 확정취소하면 다시 CREATED로
 * 돌아와 재확정할 수 있다 — 창고가 아직 손대지 않은 예정을 물리는 건 주문이 죽는 게 아니라
 * 작업지시만 회수하는 일이라서다.
 *
 * <b>취소 상태를 두지 않는다.</b> 없앨 주문은 지운다 — 확정 전이면 바로, 확정 뒤면 확정취소로
 * 되돌린 뒤에. "지운 것도 아니고 쓰는 것도 아닌" 상태를 두면 목록·집계마다 그 상태를 빼는
 * 필터가 따라붙고, 화면에서는 삭제와 취소 중 무엇을 눌러야 하는지가 매번 애매해진다.
 * (같은 이유로 마스터에서 사용여부 컬럼도 걷어냈다 — docs/schema.sql 「vendor」 참고)
 *
 * 창고 작업 진행(검수/적치)은 여기서 표현하지 않는다 — 확정으로 생성된 ASN의 IbStatus가 담당한다.
 * ASN 쪽도 취소 상태가 없다: 확정취소는 ASN 행을 삭제한다. 검수 전의 예정은 아직 아무 일도
 * 안 한 문서라 흔적 가치가 없다 (OmsIbOrderService.cancelConfirm 참고).
 */
@Getter
@RequiredArgsConstructor
public enum OmsIbStatus {
    CREATED("작성"),
    CONFIRMED("확정");

    private final String label;
}
