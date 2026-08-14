package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbWave;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OutbWaveRepository extends JpaRepository<OutbWave, Long>, OutbWaveRepositoryCustom {

    /**
     * 웨이브 행 락 — 같은 웨이브의 실행·편성 변경(자동/수동할당·주문 담기/빼기·해체)을 직렬화한다.
     * 락 없이는 두 실행이 같은 라인별 기할당 합을 읽고 각자 예약해 라인 과할당이 난다.
     * 여러 웨이브를 잠글 때는 wavId 오름차순 — 재고 행 락(재고 키 오름차순)보다 항상 먼저 잡는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from OutbWave w where w.id = :wavId")
    Optional<OutbWave> findByIdForUpdate(@Param("wavId") Long wavId);
}
