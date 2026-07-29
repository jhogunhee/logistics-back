package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.Prod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdRepository extends JpaRepository<Prod, Long>, ProdRepositoryCustom {
}
