package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.entity.OutbWave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutbWaveRepository extends JpaRepository<OutbWave, Long>, OutbWaveRepositoryCustom {

    /** 웨이브번호 채번값 발급 (outb_wave_no_seq) */
    @Query(value = "SELECT nextval('outb_wave_no_seq')", nativeQuery = true)
    Long nextWaveNoSeq();
}
