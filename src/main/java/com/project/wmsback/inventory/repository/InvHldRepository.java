package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvHld;
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

    /**
     * 다건 해제가 잠글 재고 행을 고르기 위한 사전 조회 — (보류건 id, 상품 id, 로케이션 id, Lot id).
     * 엔티티가 아니라 스칼라로 읽는 이유는 영속성 컨텍스트다 — InvHld를 락 없이 먼저 읽어두면
     * 뒤에 findByIdForUpdate로 락을 잡아도 그때 올라간 인스턴스가 그대로 나와 잔량이 갱신되지 않는다.
     */
    @Query("select h.id, h.prod.id, h.loc.id, h.lot.id from InvHld h where h.id in :ids")
    List<Object[]> findLockKeysByIdIn(@Param("ids") Collection<Long> ids);
}
