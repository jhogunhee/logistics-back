package com.project.omsback.outbound.repository;

import com.project.omsback.outbound.entity.OmsOutbOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsOutbOrderRepository extends JpaRepository<OmsOutbOrder, Long>, OmsOutbOrderRepositoryCustom {
}
