package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.entity.IbOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IbOrderRepository extends JpaRepository<IbOrder, Long>, IbOrderRepositoryCustom {

    /**
     * 주문이 현재 붙들고 있는 ASN. 확정취소 대상을 찾을 때 쓴다.
     * 확정취소가 ASN 행을 삭제하므로 주문:ASN은 항상 0..1이다
     * (DB의 uq_ib_order_oms_active 유니크 인덱스가 같은 규칙을 강제한다).
     */
    Optional<IbOrder> findByOmsIbOrderId(Long omsIbOrderId);
}
