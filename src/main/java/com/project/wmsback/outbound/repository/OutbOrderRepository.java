package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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
     * 상위 OMS 출고주문으로 창고 문서 찾기. 주문당 한 건임을 uq_outb_order_oms가 보증하므로 Optional이다.
     */
    Optional<OutbOrder> findByOmsOutbOrderId(Long omsOutbOrderId);

    /**
     * 확정취소용 행 락 — {@code requireRevertible()}이 신선한 행 위에서 판정되게 한다.
     * 판정과 삭제 사이에 편입·할당이 커밋되면 cascade로 지워진 라인을 가리키는 고아 할당이 남고
     * ({@code outb_alloc.outb_line_id}에 FK가 없다), 할당해제가 그 라인을 조인해 예약을 되돌리는
     * 구조라 예약 회수 경로가 함께 죽는다. {@code @Version}도 없어 편입/할당과 같은 이 행 락이 유일한 방어다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OutbOrder o where o.omsOutbOrderId = :omsOutbOrderId")
    Optional<OutbOrder> findByOmsOutbOrderIdForUpdate(@Param("omsOutbOrderId") Long omsOutbOrderId);

    /** 출고확정 대상 주문 — 웨이브까지 함께 읽는다(웨이브 락 순서 결정과 가드에 쓴다) */
    @Query("select o from OutbOrder o left join fetch o.wave where o.id in :ids")
    List<OutbOrder> findAllWithWaveByIds(@Param("ids") Collection<Long> ids);
}
