package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 대상 라인들의 할당 중 <b>살아 있는 지시가 붙은 것</b>의 할당 id — 할당 합산 제외 목록.
     * 여기에 합산하면 aloc_qty만 커지고 지시의 drct_qty는 그대로라 항등식이 조용히 깨지고,
     * 그 할당에 새 지시를 만들려 하면 uq_pikng_task_alloc에 걸린다.
     */
    @Query("select distinct t.outbAlloc.id from PikngTask t"
            + " where t.outbAlloc.outbLine.id in :lineIds and t.status <> :status")
    List<Long> findLiveAllocIdsByLineIds(@Param("lineIds") Collection<Long> lineIds,
                                         @Param("status") PikngTaskStatus status);

    /**
     * 웨이브의 마지막 집품 순번 — 추가 발행이 여기서 이어붙인다(「1차 동선을 다 돈 뒤 추가분」).
     * 취소된 지시도 자기 번호를 들고 남으므로 번호는 건너뛰지만 겹치지 않는다.
     */
    @Query("select coalesce(max(t.srtSeq), 0) from PikngTask t where t.wave.id = :wavId")
    int findMaxSrtSeqByWaveId(@Param("wavId") Long wavId);

    /** 실행 대상 지시의 웨이브 — 웨이브 행 락을 재고 락보다 먼저 잡기 위한 선조회 */
    @Query("select distinct t.wave.id from PikngTask t where t.id in :ids")
    List<Long> findWaveIdsByTaskIds(@Param("ids") Collection<Long> ids);

    /**
     * 실행 대상 지시 — 할당·라인·주문·스냅샷(상품/로케이션/Lot)까지 함께 읽는다.
     * 재고(inv)는 fetch하지 않는다 — 실행은 락 창구({@code InvStore.lockAllByIds})로 처음 읽어야
     * 낡은 수량을 쓰지 않고, 지연 프록시의 id는 DB를 타지 않아 락 대상 수집에 충분하다.
     */
    @Query("select t from PikngTask t"
            + " join fetch t.outbAlloc a join fetch a.outbLine l join fetch l.outbOrder"
            + " join fetch t.prod join fetch t.fromLoc join fetch t.lot"
            + " where t.id in :ids")
    List<PikngTask> findAllWithDetailsByIds(@Param("ids") Collection<Long> ids);
}
