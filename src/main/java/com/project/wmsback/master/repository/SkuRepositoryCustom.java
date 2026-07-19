package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.SkuSearchCond;
import com.project.wmsback.master.entity.Sku;

import java.util.List;
import java.util.Optional;

public interface SkuRepositoryCustom {

    List<Sku> search(SkuSearchCond cond);

    /**
     * Lot 채번(SKU+입고일자 단위 리셋) 직렬화용 로우 락.
     * 같은 SKU에 대해 동시에 검수가 들어와도 "기존 Lot 조회 → 건수 세기 → 채번" 구간이 겹치지 않도록 한다.
     */
    Optional<Sku> findByIdForUpdate(Long id);
}