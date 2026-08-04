package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.entity.PutawayTask;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 적치지시 저장·단건 조회. 조회(목록·집계)는 {@link PutawayTaskQueryRepository}가 맡는다 —
 * 동적 조건과 집계는 QueryDSL 쪽이 읽기 쉬워서 갈라 뒀다.
 */
public interface PutawayTaskRepository extends JpaRepository<PutawayTask, Long> {
}
