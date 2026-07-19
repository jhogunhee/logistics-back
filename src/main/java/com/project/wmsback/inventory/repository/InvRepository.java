package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.Inv;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvRepository extends JpaRepository<Inv, Long>, InvRepositoryCustom {

    /** 재고 키(SKU+Loc+Lot)로 스냅샷 조회 (uq_inv) */
    Optional<Inv> findBySkuIdAndLocIdAndLotId(Long skuId, Long locId, Long lotId);
}
