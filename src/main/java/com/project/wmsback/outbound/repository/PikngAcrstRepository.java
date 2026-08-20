package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.PikngAcrst;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PikngAcrstRepository extends JpaRepository<PikngAcrst, Long> {

    /** 지시의 실행 실적 로그 — 최근 실행이 위로 오게 내림차순 */
    List<PikngAcrst> findByPikngTaskIdOrderByIdDesc(Long pikngTaskId);
}
