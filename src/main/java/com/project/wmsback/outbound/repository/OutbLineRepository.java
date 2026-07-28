package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutbLineRepository extends JpaRepository<OutbLine, Long> {

    /** 주문 상세의 라인 목록 — 상품을 fetch join해 N+1 방지 */
    @Query("select l from OutbLine l join fetch l.prod where l.outbOrder.id = :orderId order by l.id")
    List<OutbLine> findAllByOutbOrderIdWithProd(Long orderId);
}
