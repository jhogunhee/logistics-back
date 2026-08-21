package com.project.wmsback.inventory.dto;

/**
 * 예약 대사 행 — 재고 키 하나에 대해 {@code inv.aloc_qty}(장부)와 원천별 미소진 합(기대값)을 나란히 놓는다.
 * 원천은 셋이다: 출고 할당(보관) · 이동지시(보관·입고 스테이징) · 피킹된 물량(출고 스테이징).
 *
 * @param alocQty   inv.aloc_qty — 행이 없으면 0 (원천은 남았는데 재고 행이 사라진 경우)
 * @param outbQty   SUM(outb_alloc.aloc_qty − pikng_qty) — 할당됐으나 아직 집품되지 않은 것
 * @param movQty    SUM(inv_mov_task.drct_qty − cmpl_qty) (DIRECTED) — 이동·적치지시가 선점한 것
 * @param stagedQty SUM(pikng_task.cmpl_qty) (살아 있는 지시, 주문 미확정) — SHIP-STAGE에 쌓인 피킹분
 * @param diff      alocQty − (outbQty + movQty + stagedQty). 0이 아니면 어긋난 행이다
 */
public record InvAlocRecResponse(
        String prodCd,
        String prodNm,
        String locCd,
        String lotNo,
        long alocQty,
        long outbQty,
        long movQty,
        long stagedQty,
        long diff
) {
}
