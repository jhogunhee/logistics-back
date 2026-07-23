package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutbOrderRepository extends JpaRepository<OutbOrder, Long>, OutbOrderRepositoryCustom {

    /**
     * 출고번호 채번값 발급. 시퀀스는 DB가 원자적으로 증가시키므로 동시 등록에도 중복이 없다.
     * (ib_no_seq와 동일 — QueryDSL로는 시퀀스.NEXTVAL 스칼라 조회를 표현할 수 없어 네이티브로 남긴다.)
     */
    @Query(value = "SELECT outb_no_seq.NEXTVAL FROM dual", nativeQuery = true)
    Long nextOutbNoSeq();

    /** 웨이브 해체 시 소속 주문 일괄 조회 */
    List<OutbOrder> findByWaveId(Long waveId);
}
