package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvLotChng;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvLotChngRepository extends JpaRepository<InvLotChng, Long>, InvLotChngRepositoryCustom {
}
