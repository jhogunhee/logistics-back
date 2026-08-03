package com.project.mdm.prod.repository;

import com.project.mdm.prod.entity.ProdUom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdUomRepository extends JpaRepository<ProdUom, Long>, ProdUomRepositoryCustom {

    /** 같은 상품에 같은 단위를 두 번 등록하는 것을 막는다 (uq_prod_uom 선제 방어) */
    boolean existsByProdIdAndUomCd(Long prodId, String uomCd);
}
