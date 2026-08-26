package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvAdj;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvAdjRepository extends JpaRepository<InvAdj, Long>, InvAdjRepositoryCustom {
}
