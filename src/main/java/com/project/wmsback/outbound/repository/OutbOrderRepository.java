package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutbOrderRepository extends JpaRepository<OutbOrder, Long>, OutbOrderRepositoryCustom {

    /** 웨이브 해체 시 소속 주문 일괄 조회 */
    List<OutbOrder> findByWaveId(Long wavId);
}
