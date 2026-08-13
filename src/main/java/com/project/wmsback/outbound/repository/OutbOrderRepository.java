package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutbOrderRepository extends JpaRepository<OutbOrder, Long>, OutbOrderRepositoryCustom {

    /** 웨이브 해체 시 소속 주문 일괄 조회 */
    List<OutbOrder> findByWaveId(Long wavId);

    /**
     * 주문 행 락 — 웨이브 편입(assignWave)의 「이미 편성됨」 가드가 신선한 행 위에서 판정되게 한다.
     * 락 없이는 동시 편성 둘이 각자 wave = NULL을 보고 통과해 마지막 커밋이 조용히 이긴다
     * (outb_order에는 @Version이 없다). 다건은 id 오름차순으로 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutbOrder o where o.id = :id")
    Optional<OutbOrder> findByIdForUpdate(@Param("id") Long id);

    /**
     * 상위 OMS 출고주문으로 창고 문서 찾기 (확정취소 경로). 주문당 한 건임을 uq_outb_order_oms가
     * 보증하므로 Optional이다.
     */
    Optional<OutbOrder> findByOmsOutbOrderId(Long omsOutbOrderId);
}
