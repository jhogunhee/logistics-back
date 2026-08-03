package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.LotAttrChng;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotAttrChngRepository extends JpaRepository<LotAttrChng, Long>, LotAttrChngRepositoryCustom {
}
