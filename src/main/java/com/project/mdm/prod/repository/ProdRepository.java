package com.project.mdm.prod.repository;

import com.project.mdm.prod.entity.Prod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdRepository extends JpaRepository<Prod, Long>, ProdRepositoryCustom {
}
