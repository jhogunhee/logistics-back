package com.project.wmsback.outbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 피킹지시 상태. 워크플로 단계만 표현한다 — 부분 실행 여부는 수량(cmplQty vs drctQty)에서 파생한다.
 * 값 집합은 PutawayTaskStatus·InvMovStatus와 같지만 재사용하지 않는다 — 다른 패키지의 상태 정의를
 * 끌어다 쓰면 두 도메인의 상태 전이가 한쪽 변경에 묶인다 (취소 규칙이 셋 다 다르다 —
 * 피킹지시만 취소가 웨이브 단위다).
 */
@Getter
@RequiredArgsConstructor
public enum PikngTaskStatus {
    DIRECTED("지시"),
    DONE("완료"),
    CANCELLED("취소");

    private final String label;
}
