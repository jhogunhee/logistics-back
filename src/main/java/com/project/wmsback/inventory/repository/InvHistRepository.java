package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvHist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvHistRepository extends JpaRepository<InvHist, Long>, InvHistRepositoryCustom {
}
