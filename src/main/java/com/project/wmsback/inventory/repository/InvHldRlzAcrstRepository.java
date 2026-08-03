package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.entity.InvHldRlzAcrst;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvHldRlzAcrstRepository extends JpaRepository<InvHldRlzAcrst, Long>, InvHldRlzAcrstRepositoryCustom {
}
