package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvMovTask;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 이동지시 저장·단건 조회. 목록은 QueryDSL(InvMovTaskRepositoryImpl), 로케이션 유입 잔량은
 * {@link LocInflowQueryRepository}가 적치지시 몫과 함께 합산한다.
 */
public interface InvMovTaskRepository extends JpaRepository<InvMovTask, Long>, InvMovTaskRepositoryCustom {
}
