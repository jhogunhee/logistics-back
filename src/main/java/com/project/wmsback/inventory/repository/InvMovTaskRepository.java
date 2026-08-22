package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvMovStatus;
import com.project.wmsback.inventory.entity.InvMovTask;
import com.project.wmsback.inventory.service.InvMovLockKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 이동지시 저장·단건 조회·확정 락. 목록은 QueryDSL(InvMovTaskRepositoryImpl), 로케이션 유입 잔량은
 * {@link LocCapacityQueryRepository}가 적치지시 몫과 함께 합산한다.
 */
public interface InvMovTaskRepository extends JpaRepository<InvMovTask, Long>, InvMovTaskRepositoryCustom {

    /**
     * 확정이 잔여수량을 검증·누적하기 전에 거는 비관적 락 — 같은 지시의 동시 확정 직렬화 지점.
     * 없으면 뒤늦은 트랜잭션이 낡은 cmpl_qty 위에 덮어써 예약과 완료수량이 어긋난다 (inv_mov_task에는 @Version이 없다).
     * 다건 확정은 재고 행을 모두 잠근 뒤 이 락을 지시 id 오름차순으로 잡는다 (보류 해제와 같은 단·같은 순서)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InvMovTask t where t.id = :id")
    Optional<InvMovTask> findByIdForUpdate(@Param("id") Long id);

    /** 피킹지시들의 살아 있는 보충지시 (RPLN, CANCELLED 제외) — 지시취소의 짝 처리 */
    List<InvMovTask> findByPikngTaskIdInAndStatusNot(Collection<Long> pikngTaskIds, InvMovStatus status);

    /**
     * 피킹지시들의 보충지시 — 상태를 가리지 않는다. 피킹 실행 가드가 쓴다: 취소된 짝을 빼고 읽으면
     * 「짝이 없는 지시」로 보여 그대로 통과한다(실물은 보관존에 있는데 지시의 from은 피킹존이다).
     * 보충지시는 발행이 피킹지시당 하나만 만들고 뒤에 더 붙지 않으므로 지시 하나에 한 행이다.
     */
    List<InvMovTask> findByPikngTaskIdIn(Collection<Long> pikngTaskIds);

    /** 보충지시 → 짝 피킹지시 id. 확정·취소가 웨이브 락을 잡기 전에 읽는 스칼라 — 엔티티로 먼저 읽으면 뒤에 거는 락이 낡은 인스턴스를 돌려준다 */
    @Query("select t.pikngTaskId from InvMovTask t where t.id in :ids and t.pikngTaskId is not null")
    List<Long> findPikngTaskIdsByIdIn(@Param("ids") Collection<Long> ids);

    /** 피킹지시들 중 보충이 확정된 건수 — 웨이브 통째 취소 가드 (보충 DONE 은 실적과 같다) */
    long countByPikngTaskIdInAndStatus(Collection<Long> pikngTaskIds, InvMovStatus status);

    /** 다건 확정이 잠글 재고 행(FROM·TO)을 고르기 위한 사전 조회. 엔티티가 아니라 프로젝션인 이유는 InvLockKey 참고 */
    @Query("select new com.project.wmsback.inventory.service.InvMovLockKey("
            + "t.id, t.prod.id, t.lot.id, t.fromLoc.id, t.toLoc.id) from InvMovTask t where t.id in :ids")
    List<InvMovLockKey> findLockKeysByIdIn(@Param("ids") Collection<Long> ids);
}
