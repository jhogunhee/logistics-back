package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvHld;
import com.project.wmsback.inventory.service.InvLockKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvHldRepository extends JpaRepository<InvHld, Long>, InvHldRepositoryCustom {

    /**
     * 해제가 잔량을 검증·차감하기 전에 거는 비관적 락 — 같은 건의 동시 해제 직렬화 지점.
     * 다건 해제는 재고 행을 모두 잠근 뒤 이 락을 보류 건 id 오름차순으로 잡는다
     * (등록은 inv 행만 잡으므로 순서 역전이 없다)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from InvHld h where h.id = :id")
    Optional<InvHld> findByIdForUpdate(@Param("id") Long id);

    /** 다건 해제가 잠글 재고 행을 고르기 위한 사전 조회. 엔티티가 아니라 프로젝션인 이유는 InvLockKey 참고 */
    @Query("select new com.project.wmsback.inventory.service.InvLockKey(h.id, h.prod.id, h.loc.id, h.lot.id) "
            + "from InvHld h where h.id in :ids")
    List<InvLockKey> findLockKeysByIdIn(@Param("ids") Collection<Long> ids);
}
