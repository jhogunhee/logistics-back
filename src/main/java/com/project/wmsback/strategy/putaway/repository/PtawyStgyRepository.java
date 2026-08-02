package com.project.wmsback.strategy.putaway.repository;

import com.project.wmsback.strategy.putaway.entity.PtawyStgy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PtawyStgyRepository extends JpaRepository<PtawyStgy, Long> {

    /** 유형 일치 전략 (유형당 1개 — UNIQUE 인덱스가 보장) */
    Optional<PtawyStgy> findByOdrDvsn(String odrDvsn);

    /** 전체 전략 (odr_dvsn IS NULL — 1개만 존재) */
    Optional<PtawyStgy> findByOdrDvsnIsNull();
}
