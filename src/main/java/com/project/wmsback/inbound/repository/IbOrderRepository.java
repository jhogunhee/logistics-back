package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IbOrderRepository extends JpaRepository<IbOrder, Long>, IbOrderRepositoryCustom {

    /**
     * 주문이 현재 붙들고 있는 유효한 ASN. 변환취소 대상을 찾을 때 쓴다.
     * 취소된 ASN은 이력으로 남겨둔 채 제외한다 — 재변환하면 같은 주문에 ASN 행이 하나 더 생기므로,
     * "취소되지 않은 것 하나"라는 이 조건이 곧 주문:ASN 1:1의 정의다
     * (DB의 uq_ib_order_oms_active 함수 기반 유니크 인덱스와 같은 규칙).
     */
    Optional<IbOrder> findByOmsIbOrderIdAndStatusNot(Long omsIbOrderId, IbStatus status);
}
