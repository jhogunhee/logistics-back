package com.project.wmsback.inbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 입고 워크플로 상태. SCHEDULED → RECEIVING → CONFIRMED — 사건이 파생 불가능한 지점만 저장한다.
 * 검수·적치의 진행(적치지시/적치완료 포함)은 상태가 아니라 라인 수량·지시에서 파생한다({@link IbPrgr}).
 * 자동 전이는 없다 — CONFIRMED는 입고확정 버튼({@code POST /inbound/asns/{id}/confirm})으로만 진입한다.
 * 취소 상태는 없다 — 확정취소는 ASN 행 자체를 삭제한다 (OmsIbOrderService.cancelConfirm).
 */
@Getter
@RequiredArgsConstructor
public enum IbStatus {
    SCHEDULED("입고예정"),
    RECEIVING("입고중"),
    // 온 것은 전부 적치 완료된 뒤 사람이 눌러 결품(예정-검수)을 못박으며 입고건을 닫는다
    CONFIRMED("입고확정");

    private final String label;
}
