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
     * 피킹지시 발행 대상 — 웨이브의 할당 중 <b>살아 있는 지시가 없는 것</b>을 스냅샷 재료(재고 키)와
     * 주문까지 함께 읽는다. 최초 발행과 추가 발행이 같은 이 창구를 쓴다.
     *
     * <p><b>「살아 있는 지시가 없다」가 inner join fetch를 안전하게 만든다.</b> 그런 할당은
     * {@code ck_aloc_qty(aloc_qty > 0)} 때문에 예약이 반드시 살아 있고, 따라서 {@code inv} 행도
     * 반드시 있다. 웨이브의 할당을 통째로 읽으면 전량 집품돼 {@code inv} 행이 삭제된 할당을
     * 조용히 떨어뜨린다 — 최초 발행(PLANNED)에서는 드러나지 않지만 추가 발행에서는 드러난다.
     */
    @Query("select a from OutbAlloc a"
            + " join fetch a.outbLine l join fetch l.outbOrder o"
            + " join fetch l.prod join fetch a.inv i join fetch i.loc lc left join fetch lc.zon join fetch i.lot"
            + " where o.wave.id = :wavId"
            + " and not exists (select 1 from PikngTask t"
            + "                  where t.outbAlloc = a and t.status <> :cancelled)")
    List<OutbAlloc> findIssuableByWaveId(@Param("wavId") Long wavId,
                                         @Param("cancelled") PikngTaskStatus cancelled);

    /**
     * 웨이브 안에서 할당을 가진 주문 id — 발행 가드(할당 0건 주문 차단)의 재료.
     * 발행 대상 조회와 갈라 두는 이유는 <b>이미 지시가 나간 할당도 세어야</b> 하기 때문이다 —
     * 추가 발행에서 발행 대상만 보고 판정하면 진행 중인 주문이 「할당 0건」으로 오판된다.
     */
    @Query("select distinct a.outbLine.outbOrder.id from OutbAlloc a"
            + " where a.outbLine.outbOrder.wave.id = :wavId")
    List<Long> findAllocatedOrderIdsByWaveId(@Param("wavId") Long wavId);

    /** 주문에 소진되지 않은 할당이 남았는지 — 0이면 전 할당 소진 (OutbOrder.recalcStatus) */
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
