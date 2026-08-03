package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvHldAcrst;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvHldAcrstRepository extends JpaRepository<InvHldAcrst, Long>, InvHldAcrstRepositoryCustom {
}
