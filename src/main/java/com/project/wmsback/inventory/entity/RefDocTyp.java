package com.project.wmsback.inventory.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 이력의 참조 문서 유형. 수동 조정(ADJUST)은 참조 문서가 없을 수 있다.
 */
@Getter
@RequiredArgsConstructor
public enum RefDocTyp {
    INBOUND("입고 문서"),
    OUTBOUND("출고 문서"),
    INV_MOV("이동지시"),
    INV_STKTK("재고조사"),
    INV_ADJ("재고조정"),
    // INV_LOT_CHNG(12자)가 아닌 이유: rfn_doc_typ이 VARCHAR(10)이라 들어가지 않는다 — 컬럼 확장 대신 이름을 줄였다
    LOT_CHNG("재고 로트변경");

    private final String label;
}
