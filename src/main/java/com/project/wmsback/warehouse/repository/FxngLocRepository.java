package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FxngLocRepository extends JpaRepository<FxngLoc, Long>, FxngLocRepositoryCustom {

    /** 로케이션 목록 응답에 고정 상품명을 싣기 위한 일괄 조회 — 상품을 함께 가져와 행당 지연 로딩을 막는다 */
    @Query("select f from FxngLoc f join fetch f.prod where f.loc in :locs")
    List<FxngLoc> findAllWithProdByLocIn(@Param("locs") Collection<Loc> locs);

    /** 수시보충 도착지 1순위 — 상품의 고정 로케이션 (로케이션을 함께 가져온다) */
    @Query("select f from FxngLoc f join fetch f.loc where f.prod.id = :prodId")
    List<FxngLoc> findAllWithLocByProdId(@Param("prodId") Long prodId);

    /** 로케이션 중복 가드 — uq_fxng_loc(한 로케이션 = 한 상품 전용)를 커밋 전에 사용자 메시지로 돌려준다 */
    Optional<FxngLoc> findByLoc(Loc loc);

    /** 로케이션 수정 가드 — 고정이 걸린 로케이션은 유형·온도대 변경과 max_qty 하향에 제약이 생긴다 */
    boolean existsByLoc(Loc loc);

    /** 주어진 로케이션 중 고정 등재된 것의 id — 정기보충 발행이 원천을 거를 때 쓴다 (스칼라라 엔티티를 올리지 않는다) */
    @Query("select f.loc.id from FxngLoc f where f.loc.id in :locIds")
    Set<Long> findLocIdsByLocIdIn(@Param("locIds") Collection<Long> locIds);
}
