package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutbAllocRepository extends JpaRepository<OutbAlloc, Long>, OutbAllocRepositoryCustom {

    /**
     * 같은 (라인, 재고) 조합의 기존 할당. 있으면 새 행을 만들지 않고 여기에 합산한다 —
     * DB에 그 조합의 UNIQUE가 없어 중복 행이 허용되지만, 같은 라인이 같은 재고를 가리키는 행이
     * 둘이면 화면과 해제 단위가 불필요하게 쪼개진다.
     */
    Optional<OutbAlloc> findByOutbLineIdAndInvId(Long outbLineId, Long invId);

    /** 대상 라인들의 기존 할당 — (라인, 재고) 키 맵을 미리 만들어 합산 대상을 한 번에 찾는다 */
    List<OutbAlloc> findByOutbLineIdIn(List<Long> outbLineIds);

    /** 주문에 남은 할당 건수 — 0이면 ALLOCATED → CREATED 복귀 (OutbOrder.revertToCreated) */
    @Query("select count(a) from OutbAlloc a where a.outbLine.outbOrder.id = :outbOrderId")
    long countByOutbOrderId(@Param("outbOrderId") Long outbOrderId);

    /** 해제 대상 조회 — 라인·주문까지 함께 읽어 상태 복귀 판정에 재조회가 없게 한다 */
    @Query("select a from OutbAlloc a join fetch a.outbLine l join fetch l.outbOrder where a.id in :ids")
    List<OutbAlloc> findAllWithLineByIds(@Param("ids") List<Long> ids);

    /**
     * 피킹지시 발행 대상 — 웨이브의 할당 전량을 스냅샷 재료(재고 키)와 주문까지 함께 읽는다.
     * 발행 시점의 웨이브는 PLANNED라 전 할당의 피킹수량이 0이고 예약이 살아 있어
     * inv 행이 반드시 존재한다 (inner join fetch가 안전한 이유).
     */
    @Query("select a from OutbAlloc a"
            + " join fetch a.outbLine l join fetch l.outbOrder o"
            + " join fetch l.prod join fetch a.inv i join fetch i.loc join fetch i.lot"
            + " where o.wave.id = :wavId")
    List<OutbAlloc> findAllWithDetailsByWaveId(@Param("wavId") Long wavId);

    /** 주문에 소진되지 않은 할당이 남았는지 — 0이면 전 할당 소진 = PICKED 전이 (OutbOrder.completePicking) */
    @Query("select count(a) from OutbAlloc a where a.outbLine.outbOrder.id = :outbOrderId and a.pikngQty < a.alocQty")
    long countUnpickedByOrderId(@Param("outbOrderId") Long outbOrderId);

    /**
     * 주문에 실적이 붙은 할당 건수 — 「이 주문이 집히기 시작했나」를 사실로 답한다.
     * 이것이 있어 {@code recalcStatus}가 ALLOCATED와 PICKING을 가르는 데 과거 상태를 쓰지 않는다.
     */
    @Query("select count(a) from OutbAlloc a where a.outbLine.outbOrder.id = :outbOrderId and a.pikngQty > 0")
    long countPickedByOrderId(@Param("outbOrderId") Long outbOrderId);

    /**
     * 주문 상태 재산출 — 세 집계를 모아 {@link OutbOrder#recalcStatus}에 넘긴다. 할당이 바뀌는 자리
     * 전부(자동할당 · 수동할당 · 할당해제 · 피킹 실행 · 결품 종결)가 이 문 하나를 지난다. 재료가 전부
     * 이 레포의 집계라 여기 둔다. 집계 전 flush는 JPQL 실행이 스스로 하므로 방금 저장·누적한
     * 할당·{@code pikng_qty}가 그대로 세어진다.
     */
    default void recalcStatus(OutbOrder order) {
        order.recalcStatus(countByOutbOrderId(order.getId()),
                countUnpickedByOrderId(order.getId()),
                countPickedByOrderId(order.getId()));
    }
}
