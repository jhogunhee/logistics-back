package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsIbOrderRepository extends JpaRepository<OmsIbOrder, Long>, OmsIbOrderRepositoryCustom {
}
