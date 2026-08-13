package com.project.wmsback.inventory.service;

/**
 * 잠글 Lot의 선조회 결과 — Lot id와, 락 순서(상품 → Lot)를 정하는 상품 id의 짝.
 * 락 없는 스칼라 프로젝션인 이유는 {@link InvLockKey}와 같다 — Lot을 엔티티로 미리 읽어두면
 * 뒤에 거는 Lot 락이 그때 올라간 낡은 인스턴스를 그대로 돌려줘 변경 전 값이 어긋날 수 있다.
 */
public record LotLockKey(Long lotId, Long prodId) {
}
