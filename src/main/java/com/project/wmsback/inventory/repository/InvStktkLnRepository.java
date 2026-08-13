package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvStktkLn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvStktkLnRepository extends JpaRepository<InvStktkLn, Long>, InvStktkLnRepositoryCustom {

    /**
     * 조사 하나의 라인 전체 — 저장·재스냅샷·확정이 공유하는 로더. 재고 키 오름차순은 처리·표시
     * 순서를 결정적으로 만들기 위한 것이다 (락 순서는 InvStore.lockAll이 내부에서 다시 정렬한다).
     */
    @Query("select l from InvStktkLn l where l.invStktk.id = :stktkId "
            + "order by l.prod.id asc, l.loc.id asc, l.lot.id asc")
    List<InvStktkLn> findByStktkIdOrderByInvKey(@Param("stktkId") Long stktkId);

    /** 같은 조사 안 재고 키 중복 검사 (uq_inv_stktk_ln이 최후 방어) */
    boolean existsByInvStktkIdAndProdIdAndLocIdAndLotId(Long invStktkId, Long prodId, Long locId, Long lotId);
}
