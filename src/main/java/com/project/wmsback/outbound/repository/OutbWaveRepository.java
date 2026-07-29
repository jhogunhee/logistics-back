package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbWave;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutbWaveRepository extends JpaRepository<OutbWave, Long>, OutbWaveRepositoryCustom {
}
