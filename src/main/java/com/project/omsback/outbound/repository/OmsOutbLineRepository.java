package com.project.omsback.outbound.repository;

import com.project.omsback.outbound.entity.OmsOutbLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsOutbLineRepository extends JpaRepository<OmsOutbLine, Long>, OmsOutbLineRepositoryCustom {

    /** 상품 삭제 가드용 — 상품 마스터가 OmsOutbProdRefChecker를 거쳐 묻는다 */
    boolean existsByProdId(Long prodId);
}
