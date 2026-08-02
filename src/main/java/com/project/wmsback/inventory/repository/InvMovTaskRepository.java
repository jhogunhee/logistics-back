package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.entity.InvMovTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvMovTaskRepository extends JpaRepository<InvMovTask, Long>, InvMovTaskRepositoryCustom {

    /**
     * TO 로케이션으로 들어올 미완료 지시 유입 잔량 SUM(drct - cmpl).
     * 적재가능수량 = max_qty − 현재고 − 이 값. 지시가 TO 용량을 컬럼 선점 없이 파생식으로 잡아두는 지점이다.
     */
    @Query("select coalesce(sum(t.drctQty - t.cmplQty), 0) from InvMovTask t "
            + "where t.toLoc.id = :locId and t.status = :status")
    long sumOpenInboundQty(@Param("locId") Long locId, @Param("status") InvMovStatus status);
}
