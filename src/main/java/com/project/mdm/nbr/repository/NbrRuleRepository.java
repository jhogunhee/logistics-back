package com.project.mdm.nbr.repository;

import com.project.mdm.nbr.entity.NbrRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NbrRuleRepository extends JpaRepository<NbrRule, String>, NbrRuleRepositoryCustom {
}
