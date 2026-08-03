package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvStktkLn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvStktkLnRepository extends JpaRepository<InvStktkLn, Long>, InvStktkLnRepositoryCustom {

    /**
     * 확정이 순회할 라인 목록. 재고 키 오름차순으로 고정해 여러 조사가 동시에 확정돼도
     * 재고 행 락을 같은 순서로 잡게 한다 (교착 방지).
     */
    @Query("select l from InvStktkLn l where l.invStktk.id = :stktkId "
            + "order by l.prod.id asc, l.loc.id asc, l.lot.id asc")
    List<InvStktkLn> findByStktkIdOrderByInvKey(@Param("stktkId") Long stktkId);

    /** 같은 조사 안 재고 키 중복 검사 (uq_inv_stktk_ln이 최후 방어) */
    boolean existsByInvStktkIdAndProdIdAndLocIdAndLotId(Long invStktkId, Long prodId, Long locId, Long lotId);
}
