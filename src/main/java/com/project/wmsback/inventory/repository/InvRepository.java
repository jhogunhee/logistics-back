package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.service.InvLockKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvRepository extends JpaRepository<Inv, Long>, InvRepositoryCustom {

    /** 재고 키(상품+Loc+Lot)로 스냅샷 조회 (uq_inv) */
    Optional<Inv> findByProdIdAndLocIdAndLotId(Long prodId, Long locId, Long lotId);

    /**
     * 재고 행 비관적 락 (재고 키 기준) — <b>InvStore 전용</b>. 서비스는 InvStore.lock/lockAll/
     * lockAllByIds를 지나야 락 순서(키 오름차순)가 한 곳에서 지켜진다. docs/design.md 「락 순서」 참고
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inv i where i.prod.id = :prodId and i.loc.id = :locId and i.lot.id = :lotId")
    Optional<Inv> findByKeyForUpdate(@Param("prodId") Long prodId, @Param("locId") Long locId, @Param("lotId") Long lotId);

    /** 잠글 행의 키 선조회 (InvStore.lockAllByIds 전용). 엔티티가 아니라 프로젝션인 이유는 InvLockKey 참고 */
    @Query("select new com.project.wmsback.inventory.service.InvLockKey(i.id, i.prod.id, i.loc.id, i.lot.id) "
            + "from Inv i where i.id in :ids")
    List<InvLockKey> findLockKeysByIdIn(@Param("ids") Collection<Long> ids);
}
