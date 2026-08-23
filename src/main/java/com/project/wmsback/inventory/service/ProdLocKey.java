package com.project.wmsback.inventory.service;

/** 상품 × 로케이션 단위 집계의 키 — 특정 상품이 특정 자리로 오고 있는 유입 잔량처럼, 로케이션 전체가 아니라 상품별로 봐야 하는 값에 쓴다 */
public record ProdLocKey(Long prodId, Long locId) {
}
