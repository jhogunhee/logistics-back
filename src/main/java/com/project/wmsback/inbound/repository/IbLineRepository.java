package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.entity.IbLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IbLineRepository extends JpaRepository<IbLine, Long>, IbLineRepositoryCustom {

    /**
     * 요청 라인들이 가리키는 상품 id (중복 제거). 잠글 상품을 고르는 용도라 라인을 엔티티가 아니라
     * <b>스칼라</b>로 읽는다 — 엔티티로 읽어버리면 뒤에 락을 걸어도 그 값이 갱신되지 않는다
     * (`ReceivingService#lockProds`). 이 입고의 라인만 돌려주므로 남의 라인 상품은 잠기지 않는다.
     */
    @Query("select distinct l.prod.id from IbLine l where l.ibOrder.id = :ibOrderId and l.id in :ibLineIds")
    List<Long> findProdIdsByOrderIdAndIdIn(@Param("ibOrderId") Long ibOrderId,
                                           @Param("ibLineIds") Collection<Long> ibLineIds);
}
