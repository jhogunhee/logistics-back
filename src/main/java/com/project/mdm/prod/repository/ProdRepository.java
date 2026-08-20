package com.project.mdm.prod.repository;

import com.project.mdm.prod.entity.Prod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProdRepository extends JpaRepository<Prod, Long>, ProdRepositoryCustom {

    Optional<Prod> findByProdCd(String prodCd);
}
