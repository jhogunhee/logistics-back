package com.project.wmsback.strategy.wave.repository;

import com.project.wmsback.strategy.wave.entity.WavStgy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WavStgyRepository extends JpaRepository<WavStgy, Long> {

    /** 실행 순서. prty 동률은 id 순으로 — 같은 값이어도 편성 결과가 결정적이어야 한다 */
    List<WavStgy> findAllByOrderByPrtyAscIdAsc();
}
