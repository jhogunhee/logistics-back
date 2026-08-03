package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutbOrderRepository extends JpaRepository<OutbOrder, Long>, OutbOrderRepositoryCustom {

    /** 웨이브 해체 시 소속 주문 일괄 조회 */
    List<OutbOrder> findByWaveId(Long wavId);

    /**
     * 상위 OMS 출고주문으로 창고 문서 찾기 (확정취소 경로). 주문당 한 건임을 uq_outb_order_oms가
     * 보증하므로 Optional이다.
     */
    Optional<OutbOrder> findByOmsOutbOrderId(Long omsOutbOrderId);
}
