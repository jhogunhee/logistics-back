package com.project.wmsback.outbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 출고 웨이브 상태. 웨이브는 피킹지시의 <b>발행 단위</b>이고 발행 이후의 진행(피킹/확정)은
 * 주문 단위로 흐른다. PLANNED(편성중, 주문 담기 가능) → ISSUED(지시가 나가 있다 — 작업중 또는
 * 확정 대기) → CLOSED(종료 — 소속 주문이 전부 출고확정됐다).
 *
 * <p>CLOSED는 2026-08-21에 생겼다. 그전에는 ISSUED가 「나갔다 · 작업중 · 끝났다」 셋을 겸해,
 * 한 지시라도 완료된 웨이브는 영원히 ISSUED였다. 종료로 가는 길은 출고확정 하나이고
 * 되돌아오는 길은 없다(출고확정 취소 없음).
 */
@Getter
@RequiredArgsConstructor
public enum WaveStatus {
    PLANNED("편성중"),
    ISSUED("지시발행"),
    CLOSED("종료");

    private final String label;
}
