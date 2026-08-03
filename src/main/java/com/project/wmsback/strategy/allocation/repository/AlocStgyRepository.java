package com.project.wmsback.strategy.allocation.repository;

import com.project.wmsback.strategy.allocation.entity.AlocStgy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlocStgyRepository extends JpaRepository<AlocStgy, Long> {

    /** 선택 순서 그대로 — 화면 정렬이 곧 매칭 판정 순서다 */
    List<AlocStgy> findAllByOrderByPrtyAscIdAsc();
}
