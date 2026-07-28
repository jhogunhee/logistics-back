package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.LocType;
import com.project.wmsback.master.entity.TempZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocRepository extends JpaRepository<Loc, Long>, LocRepositoryCustom {

    boolean existsByLocCd(String locCd);

    /** 존 삭제 가드 — 하위 로케이션이 하나라도 있으면 그 존은 지울 수 없다 (FK가 없어 DB가 막지 않는다) */
    boolean existsByZonCd(String zonCd);

    Optional<Loc> findByLocCd(String locCd);

    /** 적치 대상 로케이션 후보 (상품 온도대와 일치하는 STORAGE, 우선순위 오름차순 추천) */
    List<Loc> findAllByTmpZonAndLocTypOrderByPikngPrtyAsc(TempZone tmpZon, LocType locTyp);
}
