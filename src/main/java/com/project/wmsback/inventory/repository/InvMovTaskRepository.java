package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvMovTask;
import com.project.wmsback.inventory.service.InvMovLockKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvMovTaskRepository extends JpaRepository<InvMovTask, Long>, InvMovTaskRepositoryCustom {

    /**
     * TO 로케이션으로 들어올 미완료 지시 유입 잔량 SUM(drct - cmpl).
     * 적재가능수량 = max_qty − 현재고 − 이 값. 지시가 TO 용량을 컬럼 선점 없이 파생식으로 잡아두는 지점이다.
     */
    @Query("select coalesce(sum(t.drctQty - t.cmplQty), 0) from InvMovTask t "
            + "where t.toLoc.id = :locId "
            + "and t.status = com.project.wmsback.inventory.entity.InvMovStatus.DIRECTED")
    long sumOpenInboundQty(@Param("locId") Long locId);

    /**
     * 확정이 잔여수량을 검증·누적하기 전에 거는 비관적 락 — 같은 지시의 동시 확정 직렬화 지점.
     * 없으면 뒤늦은 트랜잭션이 낡은 cmpl_qty 위에 덮어써 예약과 완료수량이 어긋난다 (inv_mov_task에는 @Version이 없다).
     * 다건 확정은 재고 행을 모두 잠근 뒤 이 락을 지시 id 오름차순으로 잡는다 (보류 해제와 같은 단·같은 순서)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InvMovTask t where t.id = :id")
    Optional<InvMovTask> findByIdForUpdate(@Param("id") Long id);

    /** 다건 확정이 잠글 재고 행(FROM·TO)을 고르기 위한 사전 조회. 엔티티가 아니라 프로젝션인 이유는 InvLockKey 참고 */
    @Query("select new com.project.wmsback.inventory.service.InvMovLockKey("
            + "t.id, t.prod.id, t.lot.id, t.fromLoc.id, t.toLoc.id) from InvMovTask t where t.id in :ids")
    List<InvMovLockKey> findLockKeysByIdIn(@Param("ids") Collection<Long> ids);
}
