package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbAlloc;
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
}
