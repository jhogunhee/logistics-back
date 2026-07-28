package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OmsIbLineRepository extends JpaRepository<OmsIbLine, Long>, OmsIbLineRepositoryCustom {
}
