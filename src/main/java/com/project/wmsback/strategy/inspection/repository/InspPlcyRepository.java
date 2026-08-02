package com.project.wmsback.strategy.inspection.repository;

import com.project.wmsback.strategy.inspection.entity.InspPlcy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InspPlcyRepository extends JpaRepository<InspPlcy, Long> {

    /** 정책은 전역 1행 (서비스 검증, D8) — 단건 조회는 항상 이걸로 */
    Optional<InspPlcy> findFirstByOrderByIdAsc();
}
