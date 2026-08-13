package com.project.wmsback.strategy.allocation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 할당 슬롯 타입. 할당이 하는 일을 역할로 쪼갠 5종이고, 슬롯을 등록하지 않으면
 * 그 역할은 코드의 기본 동작(=현행 붙박이 로직)으로 수행된다 — <b>전략은 기본값을 덮어쓴다.</b>
 * 그래서 필수 슬롯이 없고, 전략이 0건이어도 할당은 지금과 똑같이 동작한다.
 *
 * <p>DB CHECK(ck_aloc_slot_typ)와 값을 맞춘다. 값 목록이 코드 구조 그 자체라
 * 공통코드가 아니라 CHECK로 고정한 것이고, 추가는 DDL 변경을 동반하는 구조적 enum이다.
 */
@Getter
@RequiredArgsConstructor
public enum AlocSlotTyp {

    /** 재고위치 — 후보를 계층으로 나눠 앞 계층부터 소진. 구현체 없이 조건이 곧 정의다 */
    INVN_FLTR("재고위치", true, false),

    /** 출고제약 — 후보 재고를 건별로 걸러낸다 */
    RSTRCT("출고제약", true, true),

    /** 재고 정렬 — 재고 소진 순서 */
    INVN_SRT("재고 정렬", false, true),

    /** 주문 순서 — 라인 처리 순서 (= 누가 먼저 가져가는가) */
    ODR_SRT("주문 순서", false, true),

    /** 분배 — 재고가 그룹 총요청보다 적을 때의 배분 */
    DSTRB("분배", true, true);

    private final String label;

    /** 다중 등록 가능 여부. 다중 슬롯에서는 srt_seq가 계층·실행 순서를 뜻한다 */
    private final boolean multi;

    /**
     * 구현체(cmpnt_cd)를 갖는지. INVN_FLTR만 false —
     * 「무엇을 실행할지」가 아니라 「어느 후보만」을 정하므로 정의 전체가 조건이기 때문이다.
     */
    private final boolean hasCmpnt;
}
