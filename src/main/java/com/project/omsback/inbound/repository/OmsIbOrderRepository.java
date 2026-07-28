package com.project.omsback.inbound.repository;

import com.project.omsback.inbound.entity.OmsIbOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OmsIbOrderRepository extends JpaRepository<OmsIbOrder, Long>, OmsIbOrderRepositoryCustom {

    /**
     * 입고주문 번호 채번값 발급. 시퀀스는 DB가 원자적으로 증가시키므로 동시 등록에도 중복이 없다.
     * QueryDSL은 JPA 엔티티 기반이라 시퀀스 NEXTVAL 같은 스칼라 조회는 표현할 대상이 없어 네이티브로 남긴다.
     */
    @Query(value = "SELECT nextval('oms_ib_no_seq')", nativeQuery = true)
    Long nextOmsIbNoSeq();
}
