package com.project.wmsback.outbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 출고 웨이브 상태. 웨이브는 피킹지시의 <b>발행 단위</b>이고 발행 이후의 진행(피킹/확정)은
 * 전부 주문 단위로 흐르므로, 웨이브 상태는 편성/발행 두 단계뿐이다.
 * PLANNED(편성중, 주문 담기 가능) → ISSUED(피킹지시 발행 완료)
 *
 * <p>「릴리즈(RELEASED)」가 아니라 「발행(ISSUED)」인 이유가 둘이다. 웨이브가 할당의 단위가
 * 아니라 지시의 단위라는 것이 하나이고(할당은 주문 단위 화면이 트리거한다 — design.md 「웨이브」절),
 * 같은 코드베이스에서 {@code InvHldStatus.RELEASED}가 보류 <b>해제</b>를 뜻해 한 토큰이 두 뜻으로
 * 쓰이는 것을 피하려는 것이 다른 하나다 — 사전에도 해제는 {@code RLZ}로 따로 있다.
 */
@Getter
@RequiredArgsConstructor
public enum WaveStatus {
    PLANNED("편성중"),
    ISSUED("지시발행");

    private final String label;
}
