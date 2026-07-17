package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Loc;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocRepository extends JpaRepository<Loc, Long>, LocRepositoryCustom {

    boolean existsByLocCd(String locCd);
}
