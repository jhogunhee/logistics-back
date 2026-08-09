package com.project.wmsback.inventory.service;

/**
 * 재고 행의 업무 정체성 (상품 + 로케이션 + Lot, uq_inv). 락 대상을 지목하고 잠근 행을 꺼내는 키다.
 * inv 행은 수량이 모두 0이 되면 지워졌다 다시 생기며 id가 바뀌지만 이 키는 바뀌지 않는다 —
 * 락 순서의 표준이 id가 아니라 키인 이유다 (InvStore 참고).
 */
public record InvKey(Long prodId, Long locId, Long lotId) {
}
