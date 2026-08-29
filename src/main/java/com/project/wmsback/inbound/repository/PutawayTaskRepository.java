package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.entity.PutawayTask;
import com.project.wmsback.inbound.service.PutawayLockKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 적치지시 저장·단건 조회·실행 락. 조회(목록·집계)는 {@link PutawayTaskQueryRepository}가 맡는다 —
 * 동적 조건과 집계는 QueryDSL 쪽이 읽기 쉬워서 갈라 뒀다.
 */
public interface PutawayTaskRepository extends JpaRepository<PutawayTask, Long> {

    /**
     * 실행이 잔여수량을 검증·누적하기 전에 거는 비관적 락 — 같은 지시의 동시 실행 직렬화 지점.
     * 없으면 뒤늦은 트랜잭션이 낡은 cmpl_qty 위에 덮어써 실제 이동분과 완료수량이 어긋난다
     * (putaway_task에는 @Version이 없다). 재고 행을 모두 잠근 뒤 지시 id 오름차순으로 잡는다
     * (이동확정과 같은 단·같은 순서).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PutawayTask t where t.id = :id")
    Optional<PutawayTask> findByIdForUpdate(@Param("id") Long id);

    /** 실행이 잠글 상품·재고 행을 고르기 위한 사전 조회. 엔티티가 아니라 프로젝션인 이유는 PutawayLockKey 참고 */
    @Query("select new com.project.wmsback.inbound.service.PutawayLockKey("
            + "t.id, t.ibLine.prod.id, t.lot.id, t.toLoc.id) from PutawayTask t where t.id in :ids")
    List<PutawayLockKey> findLockKeysByIdIn(@Param("ids") Collection<Long> ids);
}
