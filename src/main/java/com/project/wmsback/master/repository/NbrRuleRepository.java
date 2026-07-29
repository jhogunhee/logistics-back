package com.project.wmsback.master.repository;

import com.project.wmsback.master.entity.NbrRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NbrRuleRepository extends JpaRepository<NbrRule, String>, NbrRuleRepositoryCustom {
}
