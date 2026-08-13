package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.LotAttrChng;
import com.project.wmsback.inventory.service.LotLockKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LotAttrChngRepository extends JpaRepository<LotAttrChng, Long>, LotAttrChngRepositoryCustom {

    /**
     * 정정 다건이 잠글 (상품, Lot) 쌍의 사전 조회. 엔티티가 아니라 프로젝션인 이유는 LotLockKey 참고.
     * warehouse는 잎이라 inventory의 프로젝션을 모른다 — 그래서 LotRepository가 아니라 여기 둔다.
     */
    @Query("select new com.project.wmsback.inventory.service.LotLockKey(l.id, l.prod.id) "
            + "from Lot l where l.id in :ids")
    List<LotLockKey> findLotLockKeysByIdIn(@Param("ids") Collection<Long> ids);
}
