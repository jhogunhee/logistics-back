package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.LocType;
import com.project.wmsback.master.entity.TempZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocRepository extends JpaRepository<Loc, Long>, LocRepositoryCustom {

    boolean existsByLocCd(String locCd);

    Optional<Loc> findByLocCd(String locCd);

    /** 적치 대상 로케이션 후보 (SKU 온도대와 일치하는 STORAGE, 우선순위 오름차순 추천) */
    List<Loc> findAllByTempZoneAndLocTypeOrderByPickPrtyAsc(TempZone tempZone, LocType locType);
}
