package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface IbOrderRepository extends JpaRepository<IbOrder, Long>, IbOrderRepositoryCustom {

    /**
     * 입고번호 채번값 발급. 시퀀스는 DB가 원자적으로 증가시키므로 동시 등록에도 중복이 없다.
     * QueryDSL은 JPA 엔티티 기반이라 "시퀀스.NEXTVAL"처럼 테이블/엔티티가 없는 스칼라 조회는
     * 표현할 대상이 없다 — 네이티브 쿼리로 남긴다.
     */
    @Query(value = "SELECT nextval('ib_no_seq')", nativeQuery = true)
    Long nextIbNoSeq();

    /**
     * 주문이 현재 붙들고 있는 유효한 ASN. 변환취소 대상을 찾을 때 쓴다.
     * 취소된 ASN은 이력으로 남겨둔 채 제외한다 — 재변환하면 같은 주문에 ASN 행이 하나 더 생기므로,
     * "취소되지 않은 것 하나"라는 이 조건이 곧 주문:ASN 1:1의 정의다
     * (DB의 uq_ib_order_oms_active 함수 기반 유니크 인덱스와 같은 규칙).
     */
    Optional<IbOrder> findByOmsIbOrderIdAndStatusNot(Long omsIbOrderId, IbStatus status);
}
