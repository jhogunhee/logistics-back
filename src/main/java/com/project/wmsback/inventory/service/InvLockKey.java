package com.project.wmsback.inventory.service;

/**
 * 잠글 재고 행의 선조회 결과 — 지목한 행의 id(재고 행이면 inv id, 보류 건이면 보류 건 id)와
 * 그 재고 키의 짝. 락 없는 스칼라 프로젝션이라 영속성 컨텍스트에 엔티티를 올리지 않는다
 * (엔티티로 선조회하면 뒤에 락을 잡아도 그때 올라간 인스턴스가 그대로 나와 수량이 갱신되지 않는다).
 */
public record InvLockKey(Long id, InvKey key) {

    /** JPQL 생성자 표현식용 — select new 는 평면 인자만 받는다 */
    public InvLockKey(Long id, Long prodId, Long locId, Long lotId) {
        this(id, new InvKey(prodId, locId, lotId));
    }
}
