package com.project.wmsback.inbound.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 적치지시 상태. 워크플로 단계만 표현한다 — 부분 실행 여부는 수량(cmplQty vs drctQty)에서 파생한다.
 * 값 집합은 InvMovStatus와 같지만 재사용하지 않는다 — inventory 패키지를 inbound가 상태 정의로
 * 끌어다 쓰면 두 도메인의 상태 전이가 한쪽 변경에 묶인다 (실제로 취소 규칙이 서로 다르다).
 */
@Getter
@RequiredArgsConstructor
public enum PutawayTaskStatus {
    DIRECTED("지시"),
    DONE("완료"),
    CANCELLED("취소");

    private final String label;
}
