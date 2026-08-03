package com.project.mdm.prod.repository;

import com.project.mdm.prod.dto.ProdSearchCond;
import com.project.mdm.prod.entity.Prod;

import java.util.List;
import java.util.Optional;

public interface ProdRepositoryCustom {

    List<Prod> search(ProdSearchCond cond);

    /**
     * Lot 채번(상품+입고일자 단위 리셋) 직렬화용 로우 락.
     * 같은 상품에 대해 동시에 검수가 들어와도 "기존 Lot 조회 → 건수 세기 → 채번" 구간이 겹치지 않도록 한다.
     */
    Optional<Prod> findByIdForUpdate(Long id);
}