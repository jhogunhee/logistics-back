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

    /**
     * 라인 여러 건을 상품·입고예정과 함께 한 번에 읽는다. 라인마다 findById를 돌면 라인 수만큼
     * 쿼리가 나가고 prod·ibOrder가 LAZY라 접근할 때 또 나간다 — 검수 제약처럼 전 라인을
     * 훑는 경로에서 쓴다. 요청에 없는 id는 결과에서 빠지므로 호출부가 누락을 판단한다.
     */
    @Query("select l from IbLine l join fetch l.prod join fetch l.ibOrder where l.id in :ibLineIds")
    List<IbLine> findAllWithProdAndOrderByIdIn(@Param("ibLineIds") Collection<Long> ibLineIds);
}
