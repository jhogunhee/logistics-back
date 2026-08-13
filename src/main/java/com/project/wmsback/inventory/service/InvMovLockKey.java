package com.project.wmsback.inventory.service;

/**
 * 다건 이동확정이 잠글 재고 행의 선조회 결과. 지시 한 건이 FROM(감소)·TO(증가) 두 행을 건드리므로
 * 키가 둘인 것만 {@link InvLockKey}와 다르고, 락 없는 스칼라 프로젝션인 이유는 그쪽 설명과 같다.
 */
public record InvMovLockKey(Long taskId, InvKey fromKey, InvKey toKey) {

    /** JPQL 생성자 표현식용 — select new 는 평면 인자만 받는다 */
    public InvMovLockKey(Long taskId, Long prodId, Long lotId, Long fromLocId, Long toLocId) {
        this(taskId, new InvKey(prodId, fromLocId, lotId), new InvKey(prodId, toLocId, lotId));
    }
}
