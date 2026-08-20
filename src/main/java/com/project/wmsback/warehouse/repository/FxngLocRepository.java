package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.entity.FxngLoc;
import com.project.wmsback.warehouse.entity.Loc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FxngLocRepository extends JpaRepository<FxngLoc, Long>, FxngLocRepositoryCustom {

    /** 로케이션 중복 가드 — uq_fxng_loc(한 로케이션 = 한 상품 전용)를 커밋 전에 사용자 메시지로 돌려준다 */
    Optional<FxngLoc> findByLoc(Loc loc);

    /** 로케이션 수정 가드 — 고정이 걸린 로케이션은 유형·온도대 변경과 max_qty 하향에 제약이 생긴다 */
    boolean existsByLoc(Loc loc);
}
