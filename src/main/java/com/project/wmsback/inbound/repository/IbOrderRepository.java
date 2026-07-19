package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.entity.IbOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IbOrderRepository extends JpaRepository<IbOrder, Long>, IbOrderRepositoryCustom {

    /**
     * 입고번호 채번값 발급. 시퀀스는 DB가 원자적으로 증가시키므로 동시 등록에도 중복이 없다.
     * QueryDSL은 JPA 엔티티 기반이라 "시퀀스.NEXTVAL"처럼 테이블/엔티티가 없는 스칼라 조회는
     * 표현할 대상이 없다 — 네이티브 쿼리로 남긴다.
     */
    @Query(value = "SELECT ib_no_seq.NEXTVAL FROM dual", nativeQuery = true)
    Long nextIbNoSeq();
}
