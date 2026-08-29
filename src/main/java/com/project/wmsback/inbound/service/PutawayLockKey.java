package com.project.wmsback.inbound.service;

import com.project.wmsback.inventory.service.InvKey;

/**
 * 적치 실행이 잠글 행의 선조회 결과. 지시 한 건이 출발지(스테이징 −)·도착지(+) 두 재고 행과
 * 상품 · 입고라인을 건드리는데, 그 키들을 <b>엔티티가 아니라 스칼라로</b> 먼저 읽는다 —
 * 지시를 엔티티로 먼저 읽어두면 영속성 컨텍스트에 올라가 뒤에 거는 락이 낡은 인스턴스를 그대로
 * 돌려줘 완료수량이 갱신되지 않는다 ({@code InvMovLockKey}와 같은 이유).
 *
 * <p>출발지는 컬럼이 아니라 항상 {@code RCV-STAGE}라 로케이션 id를 밖에서 받아 키를 만든다.
 */
public record PutawayLockKey(Long taskId, Long prodId, Long lotId, Long toLocId) {

    /** 출발지(스테이징) 재고 키 */
    public InvKey stagingKey(Long stagingLocId) {
        return new InvKey(prodId, stagingLocId, lotId);
    }

    /** 도착지 재고 키. 행이 아직 없으면 락에서 빠지고 move가 만든다 */
    public InvKey targetKey() {
        return new InvKey(prodId, toLocId, lotId);
    }
}
