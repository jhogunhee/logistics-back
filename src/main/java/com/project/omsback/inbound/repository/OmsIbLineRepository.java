package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbLine;
import com.project.omsback.inbound.entity.OmsIbStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsIbLineRepository extends JpaRepository<OmsIbLine, Long>, OmsIbLineRepositoryCustom {

    /** 상품 삭제 가드용 — 상품 마스터가 OmsIbProdRefChecker를 거쳐 묻는다 */
    boolean existsByProdId(Long prodId);

    /** 단위 변경 가드용 — 상품 마스터가 OmsIbProdRefChecker를 거쳐 묻는다 */
    boolean existsByProdIdAndOmsIbOrderStatus(Long prodId, OmsIbStatus status);
}
