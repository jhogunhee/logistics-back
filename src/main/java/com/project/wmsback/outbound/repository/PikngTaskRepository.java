package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PikngTaskRepository extends JpaRepository<PikngTask, Long>, PikngTaskRepositoryCustom {

    /** 웨이브의 살아 있는 지시 (CANCELLED 제외) — 지시취소의 대상·실적 0 검증의 재료 */
    List<PikngTask> findByWaveIdAndStatusNot(Long wavId, PikngTaskStatus status);

    /**
     * 할당에 걸린 살아 있는 지시 — 할당해제 가드. 지시가 발행된 할당을 해제하면
     * 지시 행이 삭제된 할당을 가리키는 미아가 된다 (지시취소가 먼저다).
     */
    List<PikngTask> findByOutbAllocIdInAndStatusNot(Collection<Long> outbAllocIds, PikngTaskStatus status);
}
