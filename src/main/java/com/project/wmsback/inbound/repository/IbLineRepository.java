package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.entity.IbLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IbLineRepository extends JpaRepository<IbLine, Long>, IbLineRepositoryCustom {
}
