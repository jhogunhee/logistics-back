package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.entity.Zon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZonRepository extends JpaRepository<Zon, Long>, ZonRepositoryCustom {

    boolean existsByZonCd(String zonCd);
}
