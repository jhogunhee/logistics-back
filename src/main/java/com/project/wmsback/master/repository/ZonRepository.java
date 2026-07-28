package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Zon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ZonRepository extends JpaRepository<Zon, Long>, ZonRepositoryCustom {

    boolean existsByZonCd(String zonCd);

    Optional<Zon> findByZonCd(String zonCd);
}
